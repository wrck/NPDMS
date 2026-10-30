package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectTemplateInitializationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.ProjectTemplateMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttemplate.query.TemplateIdentityQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishedQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateMatchResult;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ExistingProjectRuntimeInitializer;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateSelectionService;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** Initialize an existing project exactly once from its uniquely matching current publication. */
@Service
@RequiredArgsConstructor
public class ProjectTemplateInitializationService {
    private final ProjectPlanDraftService access;
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectTemplateInitializationMapper rows;
    private final ProjectTemplateService templates;
    private final ProjectTemplateSelectionService selection;
    private final ProjectTemplateMapper templateRows;
    private final ProjectRuleFields fields;
    private final TaskStateMachineMapper states;
    private final ExistingProjectRuntimeInitializer runtime;
    private final ProjectStageAdmissionService admission;
    private final PlatformCommandExecutionApi commands;

    public record Initialize(Long projectId, Long expectedProjectVersion, Long expectedTemplateRevisionId) { }
    public record Initialized(Long projectId, Long planVersionId, Long templateId, Long templateRevisionId) { }

    public Initialized initialize(Initialize command, Long actorId, String key) {
        if (command == null || command.projectId() == null || command.expectedProjectVersion() == null
                || command.expectedProjectVersion() < 0 || command.expectedTemplateRevisionId() == null
                || key == null || key.isBlank() || key.length() > 128) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var scope = access.authorize(command.projectId(), actorId);
        var result = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(scope.tenantId(),
                        "PROJECT_TEMPLATE_INITIALIZE", actorId, key), DigestUtil.sha256Hex(JsonUtils.toJsonString(command)),
                Initialized.class, () -> initializeOnce(command, scope, actorId),
                saved -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_TEMPLATE_INITIALIZE", "Project",
                        saved.projectId().toString(), "project-template-initialize:" + key,
                        JsonUtils.toJsonString(saved), List.of()));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return result.response();
    }

    private Initialized initializeOnce(Initialize command, ProjectPlanScopeQuery scope, Long actorId) {
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(scope.tenantId(), scope.projectId()));
        access.authorize(command.projectId(), actorId);
        if (project == null || !Objects.equals(project.getTenantId(), scope.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus()) || project.getActivePlanVersionId() != null
                || project.getLifecycleTemplateId() != null || project.getLifecycleTemplateRevisionId() != null
                || project.getLifecycleTemplateRevisionNo() != null || rows.hasRuntime(scope)) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        if (!Objects.equals(project.getVersion(), command.expectedProjectVersion())) throw exception(PROJECT_VERSION_CONFLICT);
        var requested = selection.requireAvailable(command.expectedTemplateRevisionId(), scope.tenantId());
        // Serialize against publication/retirement, then recompute the unique latest candidate under that lock.
        var template = templateRows.lockTemplate(new TemplateIdentityQuery(scope.tenantId(), requested.getTemplateId()));
        if (template == null || !Objects.equals(template.getTenantId(), scope.tenantId()) || !"ACTIVE".equals(template.getStatus()))
            throw exception(PROJECT_TEMPLATE_NOT_SELECTABLE);
        var match = templates.matchPreview(fields.creationFacts(project));
        if (match.getOutcome() != TemplateMatchResult.Outcome.MATCHED || match.getMatched() == null
                || !Objects.equals(match.getMatched().getTemplateRevisionId(), requested.getId()))
            throw exception(PROJECT_TEMPLATE_NOT_SELECTABLE);
        var snapshot = templates.getExecutionSnapshot(requested.getTemplateId(), requested.getRevisionNo());
        var now = LocalDateTime.now();
        var machine = states.selectCurrentPublished(TaskStateMachinePublishedQuery.builder()
                .tenantId(scope.tenantId()).effectiveAt(now).build());
        if (machine == null) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var content = snapshot.toRuntimeContent();
        var closure = content.getClosurePolicy() == null ? null : JsonUtils.toJsonString(content.getClosurePolicy().toJson());
        if (rows.bindIfUninitialized(new ProjectTemplateInitializationMapper.Binding(scope.tenantId(), scope.projectId(),
                command.expectedProjectVersion(), requested.getTemplateId(), requested.getId(), requested.getRevisionNo(),
                content.getProcessDefinitionKey(), content.getProcessDefinitionVersion(), closure, actorId.toString())) != 1)
            throw exception(PROJECT_VERSION_CONFLICT);
        project.setLifecycleTemplateId(requested.getTemplateId());
        project.setLifecycleTemplateRevisionId(requested.getId());
        project.setLifecycleTemplateRevisionNo(requested.getRevisionNo());
        project.setClosurePolicySnapshot(closure);
        runtime.initialize(new ExistingProjectRuntimeInitializer.Initialize(project, requested, snapshot, machine.getId(), now));
        admission.activateEligible(project.getId(), actorId, "PROJECT_TEMPLATE_INITIALIZE:" + project.getId());
        return new Initialized(project.getId(), project.getActivePlanVersionId(), requested.getTemplateId(), requested.getId());
    }
}
