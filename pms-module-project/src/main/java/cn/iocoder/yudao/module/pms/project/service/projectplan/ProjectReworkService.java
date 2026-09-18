package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectStageExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeGraphFreezer;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleReevaluation;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskProgressService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;

/** One authorized rework command creates new executions, never edits old execution results or business data. */
@Service
@RequiredArgsConstructor
public class ProjectReworkService {
    @jakarta.annotation.Resource
    private ProjectCurrentStageService currentStages;
    public static final String PERMISSION = "pms:project-plan:rework";
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final ProjectMasterMapper projectRows;
    private final ProjectTaskRuntimeMapper tasks;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectTaskExecutionContractMapper contracts;
    private final ProjectStageExecutionContractMapper stageContracts;
    private final ProjectPlanProjectionMapper projections;
    private final TaskExecutionContractFactory taskFactory;
    private final ProjectRuntimeGraphFreezer stageFactory;
    private final ProjectTaskAssignmentMapper assignments;
    private final TaskStateMachineMapper states;
    private final ProjectStageInstanceMapper stages;
    private final ProjectReworkMapper changes;
    private final ProjectReworkPlanner planner;
    private final ProjectTaskProgressService progress;
    private final PlatformCommandExecutionApi commands;
    private final cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler timers;

    public record Candidate(ProjectReworkPlanner.Node node, Long executionId, Integer executionVersion,
                            Integer roundNo, String status) { }
    public record State(Long planVersionId, Integer projectVersion, List<Candidate> nodes) { }
    public record Preview(Long planVersionId, Integer projectVersion, ProjectReworkPlanner.Plan plan) { }
    public record ExpectedExecution(String nodeKey, Long executionId, Integer version) { }
    public record Apply(Long projectId, Long planVersionId, Integer expectedProjectVersion,
                        List<String> selectedNodeKeys, List<ExpectedExecution> expectedExecutions, String reason) { }
    public record NewExecution(String nodeKey, Long previousExecutionId, Long executionId, Integer roundNo) { }
    public record Result(Long projectId, Long planVersionId, Integer projectVersion, List<NewExecution> executions) { }
    private record Runtime(ProjectMasterDO project, TemplateExecutionSnapshot snapshot, List<ProjectNodeExecutionDO> rounds,
                           List<ProjectTaskInstanceDO> tasks, List<ProjectStageInstanceDO> stages) { }

    @Transactional(readOnly = true)
    public State get(Long projectId, Long actorId) {
        var runtime = load(authorize(projectId, actorId), false);
        var nodes = planner.nodes(runtime.snapshot());
        return new State(runtime.project().getActivePlanVersionId(), runtime.project().getVersion(), runtime.rounds().stream()
                .filter(round -> nodes.containsKey(round.getNodeKey()))
                .map(round -> new Candidate(nodes.get(round.getNodeKey()), round.getId(), round.getVersion(), round.getRoundNo(), round.getStatus())).toList());
    }

    @Transactional(readOnly = true)
    public Preview preview(Long projectId, List<String> selectedKeys, Long actorId) {
        var runtime = load(authorize(projectId, actorId), false);
        return new Preview(runtime.project().getActivePlanVersionId(), runtime.project().getVersion(),
                planner.plan(runtime.snapshot(), runtime.rounds(), selectedKeys));
    }

    public Result apply(Apply command, Long actorId, String key) {
        if (command == null || command.planVersionId() == null || command.expectedProjectVersion() == null
                || command.expectedExecutions() == null || command.expectedExecutions().isEmpty()
                || command.reason() == null || command.reason().isBlank() || command.reason().length() > 500
                || key == null || key.isBlank()) throw exception(PROJECT_REWORK_INVALID);
        var scope = authorize(command.projectId(), actorId);
        var execution = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(scope.tenantId(), "PROJECT_REWORK", actorId, key),
                DigestUtil.sha256Hex(JsonUtils.toJsonString(command)), Result.class,
                () -> applyOnce(command, scope, actorId), result -> new PlatformCommandExecutionApi.SuccessFacts(
                        "PROJECT_REWORK", "Project", command.projectId().toString(), "project-rework:" + key,
                        JsonUtils.toJsonString(Map.of("planVersionId", result.planVersionId(), "selectedNodeKeys", command.selectedNodeKeys(),
                                "newExecutions", result.executions(), "reason", command.reason())),
                        List.of(new ProjectRuleReevaluation(scope.tenantId(), command.projectId(), actorId, "project-rework:" + key).event())));
        if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
        if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
        return execution.response();
    }

    private Result applyOnce(Apply command, ProjectPlanScopeQuery scope, Long actorId) {
        var runtime = load(scope, true);
        authorize(command.projectId(), actorId);
        if (!Objects.equals(command.planVersionId(), runtime.project().getActivePlanVersionId())
                || !Objects.equals(command.expectedProjectVersion(), runtime.project().getVersion())) throw exception(PROJECT_REWORK_VERSION_CONFLICT);
        var plan = planner.plan(runtime.snapshot(), runtime.rounds(), command.selectedNodeKeys());
        if (!plan.applicable()) throw exception(PROJECT_REWORK_INVALID);
        Set<ExpectedExecution> actual = new HashSet<>(plan.targets().stream().map(target ->
                new ExpectedExecution(target.node().nodeKey(), target.executionId(), target.executionVersion())).toList());
        if (command.expectedExecutions().size() != actual.size() || !actual.equals(new HashSet<>(command.expectedExecutions())))
            throw exception(PROJECT_REWORK_VERSION_CONFLICT);
        var now = LocalDateTime.now();
        List<NewExecution> result = new ArrayList<>();
        for (var target : plan.targets()) {
            var old = runtime.rounds().stream().filter(round -> target.executionId().equals(round.getId())).findFirst().orElseThrow();
            Long contractId = resetProjection(runtime, target, old, scope, actorId, now);
            if (changes.retireEndedExecution(new ProjectExecutionRetire(scope.tenantId(), scope.projectId(), old.getId(), old.getVersion())) != 1)
                throw exception(PROJECT_REWORK_VERSION_CONFLICT);
            var next = new ProjectNodeExecutionDO();
            next.setId(IdWorker.getId()); next.setTenantId(scope.tenantId()); next.setProjectId(scope.projectId()); next.setPlanVersionId(command.planVersionId());
            next.setNodeKey(old.getNodeKey()); next.setNodeKind(old.getNodeKind()); next.setNodeInstanceId(old.getNodeInstanceId()); next.setContractId(contractId);
            next.setRoundNo(old.getRoundNo()+1); next.setCurrentMarker(1); next.setStatus("PENDING"); next.setVersion(0);
            next.setCreator(actorId.toString()); next.setUpdater(actorId.toString()); next.setCreateTime(now); next.setUpdateTime(now);
            if (executions.insert(next) != 1) throw exception(PROJECT_REWORK_VERSION_CONFLICT);
            result.add(new NewExecution(old.getNodeKey(), old.getId(), next.getId(), next.getRoundNo()));
        }
        if (changes.advanceProjectVersion(new ProjectReworkVersionUpdate(scope.tenantId(), scope.projectId(), command.planVersionId(),
                command.expectedProjectVersion(), actorId.toString())) != 1) throw exception(PROJECT_REWORK_VERSION_CONFLICT);
        if (plan.targets().stream().anyMatch(target -> "TASK".equals(target.node().nodeKind())))
            progress.recompute(scope.tenantId(), scope.projectId(), runtime.project().getTaskProgressVersion(), now);
        timers.schedule(scope.projectId(), command.planVersionId(), runtime.snapshot(),
                result.stream().map(NewExecution::executionId).collect(java.util.stream.Collectors.toSet()));
        return new Result(scope.projectId(), command.planVersionId(), currentStages.synchronize(scope.projectId()), List.copyOf(result));
    }

    private Long resetProjection(Runtime runtime, ProjectReworkPlanner.Target target, ProjectNodeExecutionDO old,
                                  ProjectPlanScopeQuery scope, Long actorId, LocalDateTime now) {
        if ("TASK".equals(target.node().nodeKind())) {
            var task = runtime.tasks().stream().filter(row -> Objects.equals(row.getId(), old.getNodeInstanceId())).findFirst().orElseThrow();
            if (!Set.of("DONE", "CLOSED").contains(task.getStatus()) || !Objects.equals(task.getStageCode(), target.node().stageCode()))
                throw exception(PROJECT_REWORK_INVALID);
            var contract = contracts.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(scope.tenantId(), task.getId()));
            if (contract == null || !Objects.equals(contract.getSourceNodeKey(), target.node().nodeKey())
                    || !Objects.equals(contract.getId(), old.getContractId())) throw exception(PROJECT_REWORK_INVALID);
            var definition = runtime.snapshot().toRuntimeContent().getTasks().stream()
                    .filter(node -> Objects.equals(node.getSourceNodeKey(), old.getNodeKey())).findFirst().orElseThrow();
            // A new round freezes the effective project plan, never the prior round's binding or approval result.
            var next = taskFactory.create(task.getId(), null, definition, now);
            next.setId(IdWorker.getId()); next.setTenantId(scope.tenantId());
            next.setContractVersion(contract.getContractVersion()+1);
            next.setCreator(actorId.toString()); next.setUpdater(actorId.toString());
            if (projections.closeTaskContract(new ProjectPlanProjectionMapper.ContractClosure(scope.tenantId(), scope.projectId(),
                    task.getId(), contract.getId(), contract.getVersion(), now, actorId.toString())) != 1
                    || contracts.insert(next) != 1) throw exception(PROJECT_REWORK_VERSION_CONFLICT);
            var assigned = assignments.selectCurrentForUpdate(new TaskAssignmentLockQuery(scope.tenantId(), task.getId()));
            var initialAssignment = states.requireTransition(new TaskStateTransitionQuery(scope.tenantId(), task.getStateMachineRevisionId(), "PENDING_ASSIGN", "ASSIGN"));
            String initial = assigned == null ? "PENDING_ASSIGN" : initialAssignment.getToStatusCode();
            if (changes.resetTaskProjection(new ProjectReworkTaskReset(scope.tenantId(), scope.projectId(), task.getId(), task.getVersion(),
                    task.getStatus(), initial, actorId.toString())) != 1) throw exception(PROJECT_REWORK_VERSION_CONFLICT);
            return next.getId();
        }
        var stage = runtime.stages().stream().filter(row -> Objects.equals(row.getId(), old.getNodeInstanceId())).findFirst().orElseThrow();
        if (!Set.of("DONE", "TERMINATED").contains(stage.getStatus()) || !Objects.equals(stage.getCode(), target.node().stageCode()))
            throw exception(PROJECT_REWORK_INVALID);
        var current = graph.selectContracts(new ProjectRuntimeGraphQuery(scope.tenantId(), scope.projectId())).stream()
                .filter(contract -> Objects.equals(contract.getStageId(), stage.getId()) && Objects.equals(contract.getSourceNodeKey(), target.node().nodeKey())
                        && Objects.equals(contract.getGraphVersion(), stage.getGraphVersion()) && contract.getEffectiveTo() == null).toList();
        if (current.size()!=1 || !Objects.equals(current.getFirst().getId(), old.getContractId())) throw exception(PROJECT_REWORK_INVALID);
        var contract = current.getFirst();
        var definition = runtime.snapshot().getStages().stream()
                .filter(node -> Objects.equals(node.getNodeKey(), old.getNodeKey())).findFirst().orElseThrow();
        var next = stageFactory.createContract(scope.tenantId(), scope.projectId(), definition, stage,
                JsonUtils.toJsonString(runtime.snapshot()), now);
        next.setId(IdWorker.getId()); next.setBindingVersion(contract.getBindingVersion()+1);
        next.setCreator(actorId.toString()); next.setUpdater(actorId.toString());
        if (projections.closeStageContract(new ProjectPlanProjectionMapper.ContractClosure(scope.tenantId(), scope.projectId(),
                stage.getId(), contract.getId(), contract.getVersion(), now, actorId.toString())) != 1
                || stageContracts.insert(next) != 1) throw exception(PROJECT_REWORK_VERSION_CONFLICT);
        if (stages.updateStatusIfMatch(new ProjectStageStatusUpdate(scope.tenantId(), scope.projectId(), stage.getId(), stage.getVersion(),
                stage.getStatus(), "PENDING", actorId.toString(), java.time.LocalDateTime.now())) != 1) throw exception(PROJECT_REWORK_VERSION_CONFLICT);
        return next.getId();
    }

    private ProjectPlanScopeQuery authorize(Long projectId, Long actorId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (projectId == null || projectId <= 0 || actorId == null || !permissions.hasAnyPermissions(actorId, PERMISSION))
            throw exception(PROJECT_REWORK_FORBIDDEN);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorId, projectId, ProjectScopeApi.ACTION_EDIT));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) throw exception(PROJECT_REWORK_FORBIDDEN);
        return new ProjectPlanScopeQuery(tenantId, projectId);
    }

    private Runtime load(ProjectPlanScopeQuery scope, boolean lock) {
        var project = lock ? tasks.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(scope.tenantId(), scope.projectId()))
                : projectRows.selectById(scope.projectId());
        if (project == null || !Objects.equals(project.getTenantId(), scope.tenantId()) || !"ACTIVE".equals(project.getLifecycleStatus()))
            throw exception(PROJECT_REWORK_INVALID);
        var plan = plans.selectEffective(scope);
        if (plan == null || !Objects.equals(plan.getId(), project.getActivePlanVersionId())) throw exception(PROJECT_REWORK_VERSION_CONFLICT);
        var query = new ProjectRuntimeGraphQuery(scope.tenantId(), scope.projectId());
        return new Runtime(project, TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot()),
                lock ? executions.selectCurrentForUpdate(scope) : executions.selectCurrent(scope),
                lock ? graph.selectTasksForUpdate(query) : graph.selectTasks(query),
                lock ? graph.selectStagesForUpdate(query) : graph.selectStages(query));
    }
}
