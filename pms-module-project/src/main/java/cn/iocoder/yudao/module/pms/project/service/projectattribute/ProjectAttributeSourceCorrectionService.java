package cn.iocoder.yudao.module.pms.project.service.projectattribute;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectBusinessAttributeUpdate;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeOwnerSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.ProjectAttributeSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecision;
import cn.iocoder.yudao.module.pms.project.domain.projectattribute.TemplateMatchDecisionRules;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ImpactMatchHistoryCommand;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.MatchSourceMetadata;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ProjectAttributeAdjustmentResult;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ProjectAttributeSourceCorrectionCommand;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

/** 受信任来源仅修正CRM Owner属性，并记录只读模板影响。 */
@Service
public class ProjectAttributeSourceCorrectionService {

    public static final String SOURCE_SCOPE = "INTERNAL:PROJECT_ATTRIBUTE_SOURCE_CORRECTION";

    @Resource
    private TrustedProjectServicePrincipalRegistry principalRegistry;
    @Resource
    private PlatformCommandExecutionApi commandExecutionApi;
    @Resource
    private ProjectMasterMapper projectMasterMapper;
    @Resource
    private ProjectAttributeResolutionService resolutionService;
    @Resource
    private ProjectTemplateMatchHistoryService historyService;
    @Resource
    private ProjectTemplateService templateService;

    @Transactional(rollbackFor = Exception.class)
    public ProjectAttributeAdjustmentResult correct(
            ProjectAttributeSourceCorrectionCommand command, Long tenantId, String correlationId) {
        validate(command, tenantId, correlationId);
        Long principalId = principalRegistry.resolve(command.serviceIdentity());
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        tenantId, SOURCE_SCOPE, principalId, command.idempotencyKey()),
                command.requestDigest(), ProjectAttributeAdjustmentResult.class,
                () -> correctOnce(command, tenantId, principalId, correlationId),
                result -> successFacts(command, correlationId, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        return execution.response();
    }

    private ProjectAttributeAdjustmentResult correctOnce(
            ProjectAttributeSourceCorrectionCommand command, Long tenantId,
            Long principalId, String correlationId) {
        ProjectMasterDO current = projectMasterMapper.selectByIdForUpdate(command.projectId());
        if (current == null || !tenantId.equals(current.getTenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        if (!command.expectedVersion().equals(current.getVersion())) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        ProjectAttributeSnapshot before = attributes(current);
        ProjectAttributeSnapshot after = TemplateMatchDecisionRules.requireCommonAttributes(
                new ProjectAttributeSnapshot(command.signingMethod(), current.getProjectCategory(),
                        command.implementationMode(), command.majorProjectLevel()));
        TemplateMatchDecision decision = resolutionService.evaluateImpact(after);
        Long frozenRevisionId = frozenRevisionId(current);
        if (projectMasterMapper.updateBusinessAttributesIfMatch(new ProjectBusinessAttributeUpdate(
                tenantId, current.getId(), command.expectedVersion(), after.signingMethod(),
                after.projectCategory(), after.implementationMode(), after.majorProjectLevel())) != 1) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        MatchSourceMetadata source = new MatchSourceMetadata(
                command.sourceOwner(), command.sourceSystem(), command.sourceKey(), command.sourceEventId(),
                command.sourceVersion(), command.sourceOccurredAt(), command.sourceValueDigest(),
                command.mappingVersion());
        String operationId = TemplateMatchDecisionRules.operationId(
                current.getId(), TemplateMatchDecisionRules.TRIGGER_SOURCE, command.idempotencyKey());
        historyService.appendImpact(new ImpactMatchHistoryCommand(
                tenantId, current.getId(), TemplateMatchDecisionRules.TRIGGER_SOURCE,
                before, after, ProjectAttributeOwnerSnapshot.sourceCorrection(), decision,
                frozenRevisionId, ProjectTemplateMatchHistoryService.INPUT_SOURCE, source,
                principalId, TemplateMatchDecisionRules.requireReason(command.correctionReason()),
                LocalDateTime.now(), command.idempotencyKey(), command.requestDigest(),
                operationId, correlationId, null));
        return new ProjectAttributeAdjustmentResult(current.getId(), current.getVersion() + 1,
                decision.matchResult(), TemplateMatchDecisionRules.impactResult(decision, frozenRevisionId),
                operationId);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            ProjectAttributeSourceCorrectionCommand command, String correlationId,
            ProjectAttributeAdjustmentResult result) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("projectId", result.projectId());
        detail.put("sourceSystem", command.sourceSystem());
        detail.put("sourceEventId", command.sourceEventId());
        detail.put("sourceVersion", command.sourceVersion());
        detail.put("newVersion", result.version());
        detail.put("impactResult", result.impactResult());
        return new PlatformCommandExecutionApi.SuccessFacts(
                "PROJECT_ATTRIBUTE_SOURCE_CORRECT", "Project", String.valueOf(result.projectId()),
                correlationId, JsonUtils.toJsonString(detail), null, null);
    }

    private void validate(ProjectAttributeSourceCorrectionCommand command, Long tenantId, String correlationId) {
        if (command == null || tenantId == null || command.projectId() == null
                || command.expectedVersion() == null || command.expectedVersion() < 0
                || isBlank(command.signingMethod()) || isBlank(command.implementationMode())
                || !"CRM".equals(command.sourceOwner()) || isBlank(command.sourceSystem())
                || isBlank(command.sourceKey()) || isBlank(command.sourceEventId())
                || isBlank(command.sourceVersion()) || command.sourceOccurredAt() == null
                || isBlank(command.sourceValueDigest()) || isBlank(command.mappingVersion())
                || isBlank(command.idempotencyKey()) || isBlank(command.requestDigest())
                || isBlank(command.serviceIdentity()) || isBlank(correlationId)) {
            throw new IllegalArgumentException("项目属性来源修正命令不完整");
        }
        TemplateMatchDecisionRules.requireReason(command.correctionReason());
    }

    private ProjectAttributeSnapshot attributes(ProjectMasterDO project) {
        return new ProjectAttributeSnapshot(project.getSigningMethod(), project.getProjectCategory(),
                project.getImplementationMode(), project.getMajorProjectLevel());
    }

    private Long frozenRevisionId(ProjectMasterDO project) {
        if (project.getLifecycleTemplateId() == null || project.getLifecycleTemplateRevisionNo() == null) {
            throw new IllegalArgumentException("项目缺少冻结模板版本");
        }
        return templateService.getRevisionList(project.getLifecycleTemplateId()).stream()
                .filter(revision -> project.getLifecycleTemplateRevisionNo().equals(revision.getRevisionNo()))
                .map(revision -> revision.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无法解析项目冻结模板修订"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
