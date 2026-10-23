package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishedQuery;
import cn.iocoder.yudao.module.pms.acceptance.api.deliverable.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleReevaluation;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanProjectionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** One authorized command installs the project-owned plan, audit and dedicated reevaluation Outbox event. */
@Service
@RequiredArgsConstructor
public class ProjectPlanActivationService {
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.service.operation.ProjectResultSubscriptionInstaller resultSubscriptions;
    @jakarta.annotation.Resource
    private ProjectCurrentStageService currentStages;
    @jakarta.annotation.Resource private ProjectPlanTemplateBindingService templateBinding;
    @jakarta.annotation.Resource private ProjectPlanVersionMapper planRows;
    @jakarta.annotation.Resource private ProjectPlanProjectionMapper projections;
    @jakarta.annotation.Resource private ProjectNodeExecutionMapper executionRows;
    @jakarta.annotation.Resource private ProjectRuntimeGraphMapper runtimeGraph;
    @jakarta.annotation.Resource private ProjectPlanExecutionPlanner executionPlanner;
    @jakarta.annotation.Resource private ProjectPlanDeliverablePlanner deliverablePlanner;
    @jakarta.annotation.Resource private cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi gateProcesses;
    private final ProjectPlanDraftService drafts;
    private final PlatformCommandExecutionApi commands;
    private final ProjectPlanStageTaskInstaller nodes;
    private final ProjectPlanMilestoneInstaller milestones;
    private final ProjectPlanGateInstaller gates;
    private final ProjectDeliverableInitializationApplicationService deliverables;
    private final ProjectPlanActivationPersistence activation;
    private final TaskStateMachineMapper stateMachines;
    private final ProjectTaskProgressService progress;
    private final cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler timers;

    public record Apply(Long projectId, Long draftId, ProjectPlanDraftService.Preview expectedPreview) { }
    public record Applied(Long projectId, Long planVersionId, Integer revisionNo, Long projectVersion) { }

    public record TemplateReopen(ProjectMasterDO project, Long targetRevisionId, Long actorId) { }
    public record PreparedTemplateReopen(TemplateReopen command, ProjectPlanVersionDO previousPlan,
            ProjectTemplateRevisionDO revision, TemplateExecutionSnapshot snapshot,
            List<ProjectNodeExecutionDO> executions, List<ProjectStageInstanceDO> stages,
            List<ProjectTaskInstanceDO> tasks, List<ProjectGateInstanceDO> gateRows,
            List<ProjectMilestoneInstanceDO> milestoneRows,
            ProjectDeliverableInitializationApplicationService.DeliverablePlanState deliverables,
            String previousEvidence) { }

    /** Called by the existing REOPEN command under the project lock, before its immutable snapshot is appended. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public PreparedTemplateReopen prepareTemplateReopen(TemplateReopen command) {
        var project = command.project();
        var revision = templateBinding.requireLatestForReopen(new ProjectPlanTemplateBindingService.ReopenBinding(
                project, command.targetRevisionId(), command.actorId()));
        var scope = new ProjectPlanScopeQuery(project.getTenantId(), project.getId());
        var previous = planRows.selectEffective(scope);
        if (!"NORMAL_CLOSED".equals(project.getLifecycleStatus()) || previous == null
                || !Objects.equals(previous.getId(), project.getActivePlanVersionId())
                || planRows.selectDraft(scope) != null) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        var running = gateProcesses.inspectRunning(new cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcessQuery(
                scope.tenantId(), scope.projectId()));
        if (running == null || !running.isEmpty()) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var query = new ProjectRuntimeGraphQuery(scope.tenantId(), scope.projectId());
        var rounds = executionRows.selectCurrentForUpdate(scope);
        var stageRows = runtimeGraph.selectStagesForUpdate(query);
        var taskRows = runtimeGraph.selectTasksForUpdate(query);
        var gateRows = runtimeGraph.selectGatesForUpdate(query);
        var milestoneRows = projections.selectMilestonesForUpdate(scope);
        var delivery = deliverables.inspectPlanDefinitions(project.getId());
        var evidence = new java.util.LinkedHashMap<String, Object>();
        evidence.put("project", project); evidence.put("plan", previous); evidence.put("executions", rounds);
        evidence.put("stages", stageRows); evidence.put("tasks", taskRows); evidence.put("gates", gateRows);
        evidence.put("milestones", milestoneRows); evidence.put("deliverables", delivery.definitions());
        evidence.put("targetTemplateRevisionId", revision.getId());
        return new PreparedTemplateReopen(command, previous, revision,
                TemplateExecutionSnapshotReader.read(revision.getExecutionSnapshot()), rounds, stageRows, taskRows,
                gateRows, milestoneRows, delivery, JsonUtils.toJsonString(evidence));
    }

    public record InstallTemplateReopen(PreparedTemplateReopen prepared, Long snapshotId, LocalDateTime occurredAt) { }

    /** A fresh plan uses the same installers; old projection status/result fields remain untouched. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Applied installTemplateReopen(InstallTemplateReopen command) {
        var prepared = command.prepared();
        var previousProject = prepared.command().project();
        var project = new ProjectMasterDO();
        org.springframework.beans.BeanUtils.copyProperties(previousProject, project);
        project.setVersion(previousProject.getVersion() + 1);
        project.setLifecycleStatus("ACTIVE"); project.setCurrentStage("S0"); project.setAssignmentStatus("UNASSIGNED");
        var scope = new ProjectPlanScopeQuery(project.getTenantId(), project.getId());
        var actorId = prepared.command().actorId();
        var now = command.occurredAt();
        var archive = new ProjectPlanProjectionMapper.TemplateReopenProjection(scope.tenantId(), scope.projectId(),
                prepared.previousPlan().getId(), command.snapshotId(), now, actorId.toString());
        if (projections.archiveExecutionsForTemplateReopen(archive) != prepared.executions().size()
                || projections.archiveTasksForTemplateReopen(archive) != prepared.tasks().size()
                || projections.archiveStagesForTemplateReopen(archive) != prepared.stages().size()
                || projections.archiveGatesForTemplateReopen(archive) != prepared.gateRows().size()
                || projections.archiveMilestonesForTemplateReopen(archive) != prepared.milestoneRows().size())
            throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        projections.closeTaskContractsForTemplateReopen(archive);
        projections.closeStageContractsForTemplateReopen(archive);
        deliverables.archiveForProjectReopen(new ProjectDeliverableInitializationApplicationService.ProjectReopenArchive(
                scope.projectId(), command.snapshotId(), prepared.deliverables().definitions().stream().map(
                        ProjectDeliverableInitializationApplicationService.DeliverableView::id).toList()));
        var draft = new ProjectPlanVersionDO();
        draft.setTenantId(scope.tenantId()); draft.setProjectId(scope.projectId()); draft.setStatus("DRAFT");
        draft.setRevisionNo(planRows.selectNextRevisionNo(scope)); draft.setVersion(0L);
        draft.setBasePlanVersionId(prepared.previousPlan().getId()); draft.setSourceTemplateRevisionId(prepared.revision().getId());
        draft.setDesignerDocument(prepared.revision().getDesignerDocument());
        draft.setCreator(actorId.toString()); draft.setUpdater(actorId.toString());
        if (planRows.insert(draft) != 1 || draft.getId() == null) throw exception(PROJECT_PLAN_VERSION_CONFLICT);
        var empty = new TemplateExecutionSnapshot();
        var runtime = executionPlanner.plan(prepared.previousPlan().getId(), empty, prepared.snapshot(), List.of(), List.of(), List.of());
        var delivery = deliverablePlanner.plan(scope.projectId(), empty, prepared.snapshot(),
                new ProjectDeliverableInitializationApplicationService.DeliverablePlanState(List.of(), java.util.Set.of()));
        var milestonePlan = milestones.inspect(scope, empty, prepared.snapshot());
        var gatePlan = gates.inspect(scope, empty, prepared.snapshot());
        if (!runtime.issues().isEmpty() || !delivery.issues().isEmpty() || !milestonePlan.issues().isEmpty() || !gatePlan.issues().isEmpty())
            throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var machine = stateMachines.selectCurrentPublished(TaskStateMachinePublishedQuery.builder().tenantId(scope.tenantId()).effectiveAt(now).build());
        if (machine == null) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        var installed = nodes.install(new ProjectPlanStageTaskInstaller.Request(project, draft.getId(), machine.getId(), empty,
                prepared.snapshot(), runtime, List.of(), List.of(), List.of(), actorId, now));
        deliverables.applyPlanChanges(new ProjectDeliverableInitializationApplicationService.ApplyDeliverablePlanChanges(
                scope.projectId(), actorId, delivery.writes()));
        milestones.install(scope, milestonePlan, actorId);
        String correlationId = "PROJECT_TEMPLATE_REOPEN:" + command.snapshotId();
        gates.install(scope, gatePlan, actorId, correlationId);
        activation.activateTemplateReopen(new ProjectPlanVersionMapper.TemplateReopenActivation(new ProjectPlanVersionMapper.Activation(
                scope.tenantId(), scope.projectId(), prepared.previousPlan().getId(), draft.getId(), draft.getVersion(), project.getVersion(),
                TemplateExecutionSnapshot.freezeJson(prepared.snapshot()), now, actorId.toString()), command.snapshotId()));
        if (cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionContract.present(prepared.snapshot()))
            resultSubscriptions.synchronize(scope.projectId(), draft.getId(), prepared.snapshot());
        timers.schedule(scope.projectId(), draft.getId(), prepared.snapshot(), null);
        if (installed.tasksChanged()) progress.recompute(scope.tenantId(), scope.projectId(), project.getTaskProgressVersion(), now);
        return new Applied(scope.projectId(), draft.getId(), draft.getRevisionNo(), project.getVersion() + 1);
    }

    public Applied apply(Apply command, Long actorId, String key) {
        var scope = drafts.authorize(command.projectId(),actorId);
        if (key == null || key.isBlank() || command.expectedPreview() == null
                || !Objects.equals(command.draftId(),command.expectedPreview().draftId())) throw exception(PROJECT_PLAN_CHANGE_INVALID);
        String correlationId = "PROJECT_PLAN_APPLY:" + command.projectId() + ":" + key;
        var outcome = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(scope.tenantId(),"PROJECT_PLAN_APPLY",actorId,key),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(command)),Applied.class,() -> {
                    var prepared = drafts.prepare(command.projectId(),command.draftId(),command.expectedPreview().draftVersion(),actorId,true);
                    // Compare the observed versions and impact, not merely the draft revision; runtime may have advanced.
                    if (!prepared.preview().issues().isEmpty() || !Objects.equals(command.expectedPreview(),prepared.preview()))
                        throw exception(PROJECT_PLAN_VERSION_CONFLICT);
                    LocalDateTime now = LocalDateTime.now();
                    Long newTaskStateMachineId = null;
                    if (prepared.runtime().changes().stream().anyMatch(change -> "TASK".equals(change.nodeKind())
                            && change.action() == ProjectPlanExecutionPlanner.Action.CREATE)) {
                        var machine = stateMachines.selectCurrentPublished(TaskStateMachinePublishedQuery.builder().tenantId(scope.tenantId()).effectiveAt(now).build());
                        if (machine == null) throw exception(PROJECT_PLAN_CHANGE_INVALID);
                        newTaskStateMachineId = machine.getId();
                    }
                    var installed = nodes.install(new ProjectPlanStageTaskInstaller.Request(prepared.project(),command.draftId(),newTaskStateMachineId,
                            prepared.before(),prepared.after(),prepared.runtime(),prepared.rounds(),prepared.stages(),prepared.tasks(),actorId,now));
                    deliverables.applyPlanChanges(new ProjectDeliverableInitializationApplicationService.ApplyDeliverablePlanChanges(
                            scope.projectId(),actorId,prepared.deliverables().writes()));
                    milestones.install(scope,prepared.milestones(),actorId);
                    gates.install(scope,prepared.gates(),actorId,correlationId);
                    // 快照落库必须用NON_NULL冻结口径：全局mapper会写出显式null，回读后JsonNode字段成NullNode，
                    // 冻结校验的"未配置"判断（如连线条件）随之失真。
                    activation.activate(new ProjectPlanVersionMapper.Activation(scope.tenantId(),scope.projectId(),prepared.effective().getId(),command.draftId(),
                            prepared.draft().getVersion(),prepared.project().getVersion(),
                            cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.freezeJson(prepared.after()),now,actorId.toString()),
                            installed.continuing(),installed.removed());
                    if (cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionContract.present(prepared.after()))
                        resultSubscriptions.synchronize(scope.projectId(), command.draftId(), prepared.after());
                    timers.schedule(scope.projectId(), command.draftId(), prepared.after(), null);
                    if (installed.tasksChanged()) progress.recompute(scope.tenantId(),scope.projectId(),prepared.project().getTaskProgressVersion(),now);
                    return new Applied(scope.projectId(),command.draftId(),prepared.draft().getRevisionNo(),currentStages.synchronize(scope.projectId()));
                }, applied -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_PLAN_APPLY","ProjectPlan",applied.planVersionId().toString(),correlationId,
                        JsonUtils.toJsonString(Map.of("projectId",applied.projectId(),"previousPlanVersionId",command.expectedPreview().basePlanVersionId(),
                                "planVersionId",applied.planVersionId(),"revisionNo",applied.revisionNo())),
                        List.of(new ProjectRuleReevaluation(scope.tenantId(),scope.projectId(),actorId,correlationId).event())));
        if (outcome.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (outcome.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return outcome.response();
    }
}
