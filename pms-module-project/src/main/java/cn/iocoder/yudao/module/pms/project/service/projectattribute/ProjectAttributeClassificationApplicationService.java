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
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ManualProjectAttributeAdjustmentCommand;
import cn.iocoder.yudao.module.pms.project.service.projectattribute.command.ProjectAttributeAdjustmentResult;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.ProjectRules;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_VERSION_CONFLICT;

/** 人工调整PROJ Owner属性，并只追加模板匹配影响历史。 */
@Service
public class ProjectAttributeClassificationApplicationService {

    public static final String PERMISSION_CLASSIFY = "pms:project:classify";
    public static final String CLASSIFY_SCOPE = "POST:/pms/projects/{id}/actions/classify";

    @Resource
    private PlatformCommandExecutionApi commandExecutionApi;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private ProjectManualCreationService projectService;
    @Resource
    private ProjectMasterMapper projectMasterMapper;
    @Resource
    private ProjectAttributeResolutionService resolutionService;
    @Resource
    private ProjectTemplateMatchHistoryService historyService;
    @Resource
    private ProjectTemplateService projectTemplateService;

    @Transactional(rollbackFor = Exception.class)
    public ProjectAttributeAdjustmentResult adjust(ManualProjectAttributeAdjustmentCommand command, Actor actor) {
        validate(command, actor);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_CLASSIFY)) {
            throw exception(FORBIDDEN);
        }
        projectService.getProjectForManage(command.projectId(),
                new ProjectManualCreationService.ProjectAccessActor(actor.tenantId(), actor.actorId()));
        var execution = commandExecutionApi.execute(
                new PlatformCommandExecutionApi.IdempotencyScope(
                        actor.tenantId(), CLASSIFY_SCOPE, actor.actorId(), command.idempotencyKey()),
                command.requestDigest(), ProjectAttributeAdjustmentResult.class,
                () -> adjustOnce(command, actor),
                result -> successFacts(command, actor, result));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
            throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        }
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) {
            throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        }
        return execution.response();
    }

    private ProjectAttributeAdjustmentResult adjustOnce(
            ManualProjectAttributeAdjustmentCommand command, Actor actor) {
        ProjectMasterDO current = projectMasterMapper.selectByIdForUpdate(command.projectId());
        if (current == null || !actor.tenantId().equals(current.getTenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        if (!command.expectedVersion().equals(current.getVersion())) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        ProjectAttributeSnapshot before = attributes(current);
        assertManualOwnerFields(current, command);
        ProjectAttributeSnapshot after = TemplateMatchDecisionRules.requireCommonAttributes(
                new ProjectAttributeSnapshot(command.signingMethod(), command.projectCategory(),
                        command.implementationMode(), current.getMajorProjectLevel()));
        TemplateMatchDecision decision = resolutionService.evaluateImpact(after);
        Long frozenRevisionId = frozenRevisionId(current);
        if (projectMasterMapper.updateBusinessAttributesIfMatch(new ProjectBusinessAttributeUpdate(
                actor.tenantId(), current.getId(), command.expectedVersion(), after.signingMethod(),
                after.projectCategory(), after.implementationMode(), after.majorProjectLevel())) != 1) {
            throw exception(PROJECT_VERSION_CONFLICT);
        }
        String reason = TemplateMatchDecisionRules.requireReason(command.adjustmentReason());
        String operationId = TemplateMatchDecisionRules.operationId(
                current.getId(), TemplateMatchDecisionRules.TRIGGER_MANUAL, command.idempotencyKey());
        historyService.appendImpact(new ImpactMatchHistoryCommand(
                actor.tenantId(), current.getId(), TemplateMatchDecisionRules.TRIGGER_MANUAL,
                before, after, ProjectAttributeOwnerSnapshot.classification(
                        ProjectRules.SOURCE_TYPE_MANUAL.equals(current.getSourceType())), decision,
                frozenRevisionId, ProjectTemplateMatchHistoryService.INPUT_MANUAL, null,
                actor.actorId(), reason, LocalDateTime.now(), command.idempotencyKey(),
                command.requestDigest(), operationId, actor.correlationId(), null));
        return new ProjectAttributeAdjustmentResult(current.getId(), current.getVersion() + 1,
                decision.matchResult(), TemplateMatchDecisionRules.impactResult(decision, frozenRevisionId),
                operationId);
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(
            ManualProjectAttributeAdjustmentCommand command, Actor actor,
            ProjectAttributeAdjustmentResult result) {
        var detail = new LinkedHashMap<String, Object>();
        detail.put("projectId", result.projectId());
        detail.put("newVersion", result.version());
        detail.put("matchResult", result.matchResult());
        detail.put("impactResult", result.impactResult());
        detail.put("operationId", result.operationId());
        detail.put("reason", TemplateMatchDecisionRules.requireReason(command.adjustmentReason()));
        return new PlatformCommandExecutionApi.SuccessFacts(
                "PROJECT_ATTRIBUTE_ADJUST", "Project", String.valueOf(result.projectId()),
                actor.correlationId(), JsonUtils.toJsonString(detail), null, null);
    }

    private void validate(ManualProjectAttributeAdjustmentCommand command, Actor actor) {
        if (command == null || command.projectId() == null || command.expectedVersion() == null
                || command.expectedVersion() < 0 || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank() || command.requestDigest() == null
                || command.requestDigest().isBlank() || actor == null || actor.tenantId() == null
                || actor.actorId() == null || actor.correlationId() == null
                || actor.correlationId().isBlank()) {
            throw new IllegalArgumentException("项目属性调整命令不完整");
        }
        TemplateMatchDecisionRules.requireReason(command.adjustmentReason());
        TemplateMatchDecisionRules.requireCommonAttributes(new ProjectAttributeSnapshot(
                command.signingMethod(), command.projectCategory(), command.implementationMode(), null));
    }

    private ProjectAttributeSnapshot attributes(ProjectMasterDO project) {
        return new ProjectAttributeSnapshot(project.getSigningMethod(), project.getProjectCategory(),
                project.getImplementationMode(), project.getMajorProjectLevel());
    }

    private void assertManualOwnerFields(
            ProjectMasterDO current, ManualProjectAttributeAdjustmentCommand command) {
        if (!ProjectRules.SOURCE_TYPE_MANUAL.equals(current.getSourceType())
                && (!java.util.Objects.equals(current.getSigningMethod(), command.signingMethod().trim())
                || !java.util.Objects.equals(current.getImplementationMode(), command.implementationMode().trim()))) {
            throw exception(FORBIDDEN);
        }
    }

    private Long frozenRevisionId(ProjectMasterDO project) {
        if (project.getLifecycleTemplateId() == null || project.getLifecycleTemplateRevisionNo() == null) {
            throw new IllegalArgumentException("项目缺少冻结模板版本");
        }
        return projectTemplateService.getRevisionList(project.getLifecycleTemplateId()).stream()
                .filter(revision -> project.getLifecycleTemplateRevisionNo().equals(revision.getRevisionNo()))
                .map(revision -> revision.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无法解析项目冻结模板修订"));
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
