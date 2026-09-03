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
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleIdentityLockQuery;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.audit.InspectionRulePublicationAuditService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleActionPermissionGuard;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleSecurityReviewPermissionGuard;
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
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_DRAFT_INVALID;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_IDEMPOTENCY_CONFLICT;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_REVISION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_REVISION_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class InspectionRulePublicationServiceImpl implements InspectionRulePublicationService {

    static final String REVIEW_SCOPE = "INSPECTION_RULE_SECURITY_REVIEW";
    static final String REVIEW_OPERATION = "INSPECTION_RULE_SECURITY_REVIEW";
    static final String PUBLISH_SCOPE = "INSPECTION_RULE_PUBLISH";
    static final String PUBLISH_OPERATION = "INSPECTION_RULE_PUBLISH";
    static final String DISABLE_SCOPE = "INSPECTION_RULE_DISABLE";
    static final String DISABLE_OPERATION = "INSPECTION_RULE_DISABLE";
    private static final String AGGREGATE_TYPE = "InspectionRuleRevision";

    private final InspectionRuleRevisionMapper revisionMapper;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final InspectionRulePublicationAuditService publicationAuditService;
    private final InspectionRuleActionPermissionGuard permissionGuard;
    private final InspectionRulePublicationTransactionService transactionService;
    private final InspectionRuleSecurityReviewPermissionGuard securityReviewPermissionGuard;

    @Override
    public ReviewResult recordSecurityReview(ReviewCommand command) {
        validateReviewCommand(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        try {
            InspectionRuleSecurityReviewPermissionGuard.ReviewAuthorization authorization =
                    securityReviewPermissionGuard.check();
            actorId = authorization.actorId();
            Long auditedActorId = actorId;
            PlatformCommandExecutionApi.ExecutionResult<ReviewResult> execution = commandExecutionApi.execute(
                    new PlatformCommandExecutionApi.IdempotencyScope(
                            tenantId, REVIEW_SCOPE, auditedActorId, command.idempotencyKey()),
                    requestDigest(command), ReviewResult.class,
                    () -> {
                        InspectionRulePublicationTransactionService.SecurityReviewResult result =
                                transactionService.recordSecurityReview(
                                        new InspectionRulePublicationTransactionService.SecurityReviewCommand(
                                                tenantId, command.revisionId(), command.expectedVersion(),
                                                command.conclusionCode(), authorization, LocalDateTime.now()));
                        return new ReviewResult(
                                result.reviewReference(), result.revisionId(), result.contentDigest(),
                                result.conclusionCode(), result.reviewedAt(), false);
                    },
                    result -> reviewSuccessFacts(command, result, authorization));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
                throw exception(INSPECTION_RULE_IDEMPOTENCY_CONFLICT);
            }
            if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                    || execution.response() == null) {
                throw exception(INSPECTION_RULE_IDEMPOTENCY_IN_PROGRESS);
            }
            ReviewResult result = execution.response();
            return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                    ? new ReviewResult(
                    result.reviewReference(), result.revisionId(), result.contentDigest(),
                    result.conclusionCode(), result.reviewedAt(), true)
                    : result;
        } catch (RuntimeException failure) {
            auditRejected(
                    tenantId, actorId, command.revisionId(), command.expectedVersion(),
                    command.correlationId(), REVIEW_OPERATION, failure,
                    Map.of("conclusionCode", command.conclusionCode()));
            throw failure;
        }
    }

    @Override
    public PublishResult publish(PublishCommand command) {
        validatePublishCommand(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        try {
            permissionGuard.checkPublish();
            InspectionRuleRevisionDO target = requireRevision(command.revisionId(), tenantId);
            InspectionRuleRevisionDO currentPublished = revisionMapper.selectCurrentPublishedByRule(
                    new InspectionRuleIdentityLockQuery(tenantId, target.getRuleId()));
            Long expectedPublishedRevisionId = currentPublished == null ? null : currentPublished.getId();
            PlatformCommandExecutionApi.ExecutionResult<PublishResult> execution = commandExecutionApi.execute(
                    new PlatformCommandExecutionApi.IdempotencyScope(
                            tenantId, PUBLISH_SCOPE, actorId, command.idempotencyKey()),
                    requestDigest(command), PublishResult.class,
                    () -> {
                        InspectionRulePublicationTransactionService.ApprovedPublishResult result =
                                transactionService.publishApproved(
                                        new InspectionRulePublicationTransactionService.ApprovedPublishCommand(
                                                tenantId, command.revisionId(), command.expectedVersion(),
                                                expectedPublishedRevisionId, actorId, LocalDateTime.now()));
                        return new PublishResult(
                                result.revisionId(), "PUBLISHED", result.version(), result.disabledRevisionId(),
                                result.contentDigest(), result.reviewReference(), false);
                    },
                    result -> publishSuccessFacts(command, result, actorId));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
                throw exception(INSPECTION_RULE_IDEMPOTENCY_CONFLICT);
            }
            if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                    || execution.response() == null) {
                throw exception(INSPECTION_RULE_IDEMPOTENCY_IN_PROGRESS);
            }
            PublishResult result = execution.response();
            return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                    ? new PublishResult(
                    result.revisionId(), result.statusCode(), result.version(), result.disabledRevisionId(),
                    result.contentDigest(), result.reviewReference(), true)
                    : result;
        } catch (RuntimeException failure) {
            auditRejected(
                    tenantId, actorId, command.revisionId(), command.expectedVersion(),
                    command.correlationId(), PUBLISH_OPERATION, failure, Map.of());
            throw failure;
        }
    }

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

    private PlatformCommandExecutionApi.SuccessFacts reviewSuccessFacts(
            ReviewCommand command,
            ReviewResult result,
            InspectionRuleSecurityReviewPermissionGuard.ReviewAuthorization authorization) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("revisionId", result.revisionId());
        detail.put("contentDigest", result.contentDigest());
        detail.put("reviewReference", result.reviewReference());
        detail.put("conclusionCode", result.conclusionCode());
        detail.put("reviewedAt", result.reviewedAt());
        detail.put("actorId", authorization.actorId());
        detail.put("permissionCode", authorization.permissionCode());
        detail.put("authorizationType", authorization.authorizationType());
        detail.put("authorizationSourceId", authorization.authorizationSourceId());
        detail.put("revisionVersion", command.expectedVersion());
        return new PlatformCommandExecutionApi.SuccessFacts(
                REVIEW_OPERATION, AGGREGATE_TYPE, String.valueOf(result.revisionId()),
                command.correlationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private PlatformCommandExecutionApi.SuccessFacts publishSuccessFacts(
            PublishCommand command,
            PublishResult result,
            Long actorId) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("revisionId", result.revisionId());
        detail.put("statusBefore", "DRAFT");
        detail.put("statusAfter", result.statusCode());
        detail.put("revisionVersionBefore", command.expectedVersion());
        detail.put("revisionVersionAfter", result.version());
        detail.put("disabledRevisionId", result.disabledRevisionId());
        detail.put("contentDigest", result.contentDigest());
        detail.put("reviewReference", result.reviewReference());
        detail.put("actorId", actorId);
        return new PlatformCommandExecutionApi.SuccessFacts(
                PUBLISH_OPERATION, AGGREGATE_TYPE, String.valueOf(result.revisionId()),
                command.correlationId(), JsonUtils.toJsonString(detail), null, null);
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
        auditRejected(
                tenantId, actorId, command.revisionId(), command.expectedVersion(),
                command.correlationId(), DISABLE_OPERATION, failure, Map.of());
    }

    private void auditRejected(
            Long tenantId,
            Long actorId,
            Long revisionId,
            Integer expectedVersion,
            String correlationId,
            String operation,
            RuntimeException failure,
            Map<String, ?> additionalDetail) {
        if (actorId == null || actorId <= 0) {
            return;
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("revisionId", revisionId);
        detail.put("expectedVersion", expectedVersion);
        detail.putAll(additionalDetail);
        detail.put("errorCode", failure instanceof ServiceException serviceException
                ? String.valueOf(serviceException.getCode()) : operation + "_FAILED");
        publicationAuditService.recordRejected(
                tenantId, actorId, correlationId, operation, String.valueOf(revisionId), detail);
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

    private static void validateReviewCommand(ReviewCommand command) {
        if (command == null
                || command.revisionId() == null || command.revisionId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || !Set.of("PASSED", "REJECTED").contains(command.conclusionCode())
                || invalidCommandIdentity(command.idempotencyKey(), command.correlationId())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
    }

    private static void validatePublishCommand(PublishCommand command) {
        if (command == null
                || command.revisionId() == null || command.revisionId() <= 0
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || invalidCommandIdentity(command.idempotencyKey(), command.correlationId())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
    }

    private static boolean invalidCommandIdentity(String idempotencyKey, String correlationId) {
        return idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 128;
    }

    private static String requestDigest(DisableCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("revisionId", command.revisionId());
        payload.put("expectedVersion", command.expectedVersion());
        return sha256(JsonUtils.toJsonString(payload));
    }

    private static String requestDigest(ReviewCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("revisionId", command.revisionId());
        payload.put("expectedVersion", command.expectedVersion());
        payload.put("conclusionCode", command.conclusionCode());
        return sha256(JsonUtils.toJsonString(payload));
    }

    private static String requestDigest(PublishCommand command) {
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
