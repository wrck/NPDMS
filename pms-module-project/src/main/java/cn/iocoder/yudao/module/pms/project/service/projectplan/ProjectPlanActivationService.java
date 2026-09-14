package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateMachinePublishedQuery;
import cn.iocoder.yudao.module.pms.project.service.acceptance.application.ProjectDeliverableInitializationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleReevaluation;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** One authorized command installs the project-owned plan, audit and dedicated reevaluation Outbox event. */
@Service
@RequiredArgsConstructor
public class ProjectPlanActivationService {
    private final ProjectPlanDraftService drafts;
    private final PlatformCommandExecutionApi commands;
    private final ProjectPlanStageTaskInstaller nodes;
    private final ProjectPlanMilestoneInstaller milestones;
    private final ProjectPlanGateInstaller gates;
    private final ProjectDeliverableInitializationApplicationService deliverables;
    private final ProjectPlanActivationPersistence activation;
    private final TaskStateMachineMapper stateMachines;
    private final ProjectTaskProgressService progress;

    public record Apply(Long projectId, Long draftId, ProjectPlanDraftService.Preview expectedPreview) { }
    public record Applied(Long projectId, Long planVersionId, Integer revisionNo, Integer projectVersion) { }

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
                    activation.activate(new ProjectPlanVersionMapper.Activation(scope.tenantId(),scope.projectId(),prepared.effective().getId(),command.draftId(),
                            prepared.draft().getVersion(),prepared.project().getVersion(),JsonUtils.toJsonString(prepared.after()),now,actorId.toString()),
                            installed.continuing(),installed.removed());
                    if (installed.tasksChanged()) progress.recompute(scope.tenantId(),scope.projectId(),prepared.project().getTaskProgressVersion(),now);
                    return new Applied(scope.projectId(),command.draftId(),prepared.draft().getRevisionNo(),prepared.project().getVersion()+1);
                }, applied -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_PLAN_APPLY","ProjectPlan",applied.planVersionId().toString(),correlationId,
                        JsonUtils.toJsonString(Map.of("projectId",applied.projectId(),"previousPlanVersionId",command.expectedPreview().basePlanVersionId(),
                                "planVersionId",applied.planVersionId(),"revisionNo",applied.revisionNo())),
                        List.of(new ProjectRuleReevaluation(scope.tenantId(),scope.projectId(),actorId,correlationId).event())));
        if (outcome.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (outcome.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return outcome.response();
    }
}
