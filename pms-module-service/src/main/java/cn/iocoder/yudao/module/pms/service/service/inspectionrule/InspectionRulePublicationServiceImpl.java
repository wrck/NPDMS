package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDisableUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection.InspectionRulePublicationLockProjection;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRulePublicationLockQuery;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.audit.InspectionRulePublicationAuditService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleActionPermissionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_DRAFT_INVALID;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_REVISION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_REVISION_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class InspectionRulePublicationServiceImpl implements InspectionRulePublicationService {

    static final String DISABLE_SCOPE = "INSPECTION_RULE_DISABLE";
    static final String DISABLE_OPERATION = "INSPECTION_RULE_DISABLE";
    private static final String AGGREGATE_TYPE = "InspectionRuleRevision";

    private final InspectionRuleRevisionMapper revisionMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final InspectionRulePublicationAuditService publicationAuditService;
    private final InspectionRuleActionPermissionGuard permissionGuard;

    @Override
    public DisableResult disable(DisableCommand command) {
        validateCommand(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        try {
            permissionGuard.checkDisable();
            PlatformCommandExecutionApi.ExecutionResult<DisableResult> execution = commandExecutionApi.execute(
                    new PlatformCommandExecutionApi.IdempotencyScope(
                            tenantId, DISABLE_SCOPE, actorId, command.idempotencyKey()),
                    requestDigest(command), DisableResult.class,
                    () -> disableOnce(command, tenantId, actorId),
                    result -> successFacts(command, result, actorId));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
                throw exception(INSPECTION_RULE_IDEMPOTENCY_CONFLICT);
            }
            if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                    || execution.response() == null) {
                throw exception(INSPECTION_RULE_IDEMPOTENCY_IN_PROGRESS);
            }
            DisableResult result = execution.response();
            return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                    ? new DisableResult(result.revisionId(), result.statusCode(), result.version(), true)
                    : result;
        } catch (RuntimeException failure) {
            auditRejected(tenantId, actorId, command, failure);
            throw failure;
        }
    }

    private DisableResult disableOnce(
            DisableCommand command,
            Long tenantId,
            Long actorId) {
        InspectionRuleRevisionDO inspected = requireRevision(command.revisionId(), tenantId);
        if (!Objects.equals(inspected.getVersion(), command.expectedVersion())) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        if (!"PUBLISHED".equals(inspected.getStatusCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        InspectionRulePublicationLockProjection locked = revisionMapper.selectPublicationLockForUpdate(
                new InspectionRulePublicationLockQuery(tenantId, inspected.getRuleId(), inspected.getId()));
        if (locked == null || !Objects.equals(locked.targetRevisionId(), inspected.getId())) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        if (!Objects.equals(locked.currentPublishedRevisionId(), inspected.getId())
                || !"PUBLISHED".equals(locked.targetRevisionStatusCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        if (!Objects.equals(locked.targetRevisionVersion(), command.expectedVersion())) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        if (revisionMapper.disablePublishedIfMatch(new InspectionRuleDisableUpdate(
                tenantId, inspected.getId(), command.expectedVersion(), actorId, LocalDateTime.now())) != 1) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        return new DisableResult(inspected.getId(), "DISABLED", command.expectedVersion() + 1, false);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            DisableCommand command,
            DisableResult result,
            Long actorId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("revisionId", result.revisionId());
        detail.put("statusBefore", "PUBLISHED");
        detail.put("statusAfter", result.statusCode());
        detail.put("revisionVersionBefore", command.expectedVersion());
        detail.put("revisionVersionAfter", result.version());
        detail.put("actorId", actorId);
        return new PlatformCommandExecutionApi.SuccessFacts(
                DISABLE_OPERATION, AGGREGATE_TYPE, String.valueOf(result.revisionId()),
                command.correlationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private void auditRejected(Long tenantId, Long actorId, DisableCommand command, RuntimeException failure) {
        if (actorId == null || actorId <= 0) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("revisionId", command.revisionId());
        detail.put("expectedVersion", command.expectedVersion());
        detail.put("errorCode", failure instanceof ServiceException serviceException
                ? String.valueOf(serviceException.getCode()) : "INSPECTION_RULE_DISABLE_FAILED");
        publicationAuditService.recordRejected(tenantId, actorId, command.correlationId(), DISABLE_OPERATION,
                String.valueOf(command.revisionId()), detail);
    }

    private InspectionRuleRevisionDO requireRevision(Long revisionId, Long tenantId) {
        InspectionRuleRevisionDO revision = revisionMapper.selectById(revisionId);
        if (revision == null || !Objects.equals(revision.getTenantId(), tenantId)) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        return revision;
    }

    private static void validateCommand(DisableCommand command) {
        if (command == null || command.revisionId() == null || command.revisionId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > 128
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
    }

    private static String requestDigest(DisableCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("revisionId", command.revisionId());
        payload.put("expectedVersion", command.expectedVersion());
        return sha256(JsonUtils.toJsonString(payload));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256摘要算法不可用", exception);
        }
    }
}
