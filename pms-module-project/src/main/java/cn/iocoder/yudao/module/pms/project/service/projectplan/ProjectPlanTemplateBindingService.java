package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.query.TemplateIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateMatchRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** Import immutable published content and its identity together; activation remains a separate command. */
@Service
@RequiredArgsConstructor
public class ProjectPlanTemplateBindingService {
    private final ProjectPlanDraftService drafts;
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectTemplateMapper templates;
    private final ProjectTemplateRevisionMapper revisions;
    private final ProjectTemplateMatchRuleEvaluator matches;
    private final ProjectRuleFields fields;
    private final PlatformCommandExecutionApi commands;

    public record Import(Long projectId, Long draftId, Long expectedVersion,
                         Long expectedBasePlanVersionId, Long targetTemplateRevisionId) { }
    public record Imported(Long projectId, Long draftId, Long draftVersion,
                           Long previousTemplateRevisionId, Long sourceTemplateRevisionId) { }

    record ReopenBinding(cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO project,
                         Long targetRevisionId, Long actorId) { }

    cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO requireLatestForReopen(ReopenBinding command) {
        var scope = drafts.authorize(command.project().getId(), command.actorId());
        var requested = revisions.selectById(command.targetRevisionId());
        if (requested == null || !Objects.equals(scope.tenantId(), requested.getTenantId())
                || !"PUBLISHED".equals(requested.getStatus())) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var template = templates.lockTemplate(new TemplateIdentityQuery(scope.tenantId(), requested.getTemplateId()));
        if (template == null || !Objects.equals(scope.tenantId(), template.getTenantId()) || !"ACTIVE".equals(template.getStatus()))
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var published = revisions.selectPublishedListByTemplateId(template.getId());
        if (published.isEmpty()) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var revision = published.getFirst();
        if (!Objects.equals(scope.tenantId(), revision.getTenantId()) || !Objects.equals(command.targetRevisionId(), revision.getId())
                || !Integer.valueOf(TemplateDesignerDocument.SCHEMA_VERSION).equals(revision.getDesignerSchemaVersion()))
            throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        var snapshot = TemplateExecutionSnapshotReader.read(revision.getExecutionSnapshot());
        var starts = snapshot.getStages().stream().filter(stage -> Boolean.TRUE.equals(stage.getStart())).toList();
        if (starts.size() != 1 || !"S0".equals(starts.getFirst().getLifecycleStage()))
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        if (!matches.evaluate(scope.tenantId(), revision.getId(), snapshot, fields.creationFacts(command.project()).values()).matched())
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        return revision;
    }

    public Imported importPublished(Import command, Long actorId, String key) {
        if (command == null || command.expectedVersion() == null || command.expectedBasePlanVersionId() == null
                || command.targetTemplateRevisionId() == null || key == null || key.isBlank())
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var scope = drafts.authorize(command.projectId(), actorId);
        var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        scope.tenantId(), "PROJECT_PLAN_TEMPLATE_BIND", actorId, key),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(command)), Imported.class,
                () -> importOnce(command, scope, actorId), saved -> new PlatformCommandExecutionApi.SuccessFacts(
                        "PROJECT_PLAN_TEMPLATE_BIND", "ProjectPlan", saved.draftId().toString(),
                        "project-plan-template-bind:" + key, JsonUtils.toJsonString(saved), List.of()));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return result.response();
    }

    private Imported importOnce(Import command, ProjectPlanScopeQuery scope, Long actorId) {
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(scope.tenantId(), scope.projectId()));
        drafts.authorize(command.projectId(), actorId);
        if (project == null || !Objects.equals(project.getTenantId(), scope.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var effective = plans.selectEffective(scope);
        var draft = plans.selectDraft(scope);
        if (effective == null || draft == null || !Objects.equals(command.draftId(), draft.getId())
                || !Objects.equals(command.expectedVersion(), draft.getVersion())
                || !Objects.equals(command.expectedBasePlanVersionId(), effective.getId())
                || !Objects.equals(effective.getId(), project.getActivePlanVersionId())
                || !Objects.equals(effective.getId(), draft.getBasePlanVersionId())) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        var revision = revisions.selectById(command.targetTemplateRevisionId());
        if (revision == null || !Objects.equals(scope.tenantId(), revision.getTenantId())
                || !"PUBLISHED".equals(revision.getStatus())
                || !Integer.valueOf(TemplateDesignerDocument.SCHEMA_VERSION).equals(revision.getDesignerSchemaVersion()))
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var template = templates.lockTemplate(new TemplateIdentityQuery(scope.tenantId(), revision.getTemplateId()));
        if (template == null || !Objects.equals(scope.tenantId(), template.getTenantId()) || !"ACTIVE".equals(template.getStatus()))
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var snapshot = TemplateExecutionSnapshotReader.read(revision.getExecutionSnapshot());
        if (!matches.evaluate(scope.tenantId(), revision.getId(), snapshot, fields.creationFacts(project).values()).matched())
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        if (plans.bindTemplateDraftIfCurrent(new ProjectPlanVersionMapper.TemplateDraftBinding(scope.tenantId(), scope.projectId(),
                draft.getId(), draft.getVersion(), effective.getId(), revision.getId(), revision.getDesignerDocument(), actorId.toString())) != 1)
            throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        return new Imported(scope.projectId(), draft.getId(), draft.getVersion() + 1, draft.getSourceTemplateRevisionId(), revision.getId());
    }
}
