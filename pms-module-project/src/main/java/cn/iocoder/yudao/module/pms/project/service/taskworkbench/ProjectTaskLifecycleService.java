package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskCompletionEvaluationDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskCompletionEvaluationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCommandQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskByIdQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskCompletionFactsQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskLifecycleStateUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskStateTransitionQuery;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskNativeCompletionPolicy;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.event.TaskCompletedMessage;
import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.AcceptanceActivityCompletionFactApi;
import cn.iocoder.yudao.module.pms.acceptance.api.acceptanceactivity.dto.AcceptanceActivityCompletionCommand;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_IN_PROGRESS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_DEPENDENCY_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_INCOMPLETE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_REPORT_VERSION_CONFLICT;

/** 项目任务动作、原生/业务/订阅完成判定、审计与Outbox应用服务。 */
@Service
@RequiredArgsConstructor
public class ProjectTaskLifecycleService {
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler timers;

    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService stageAdmissionService;
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper nodeExecutions;

    @jakarta.annotation.Resource
    private ProjectTaskApprovalService taskApprovals;

    private static final Set<String> ACTIONS = Set.of("START", "SUBMIT", "COMPLETE", "CANCEL", "APPROVAL");
    private static final String COMMAND_SCOPE = "POST:/api/v1/pms/project-tasks/{id}/actions/{action}";

    private final ProjectTaskRuntimeMapper taskMapper;
    private final ProjectTaskExecutionContractMapper contractMapper;
    private final ProjectTaskAssignmentMapper assignmentMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTaskCompletionEvaluationMapper evaluationMapper;
    private final TaskStateMachineMapper stateMachineMapper;
    private final TaskNativeBindingHostProvider nativeProvider;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final ProjectTaskProgressService progressService;
    private final PermissionApi permissionApi;
    private final AcceptanceActivityCompletionFactApi acceptanceActivityCompletionFactApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectTaskPlanCompletionService planCompletion;
    private final TaskBusinessBindingHostProvider businessProvider;
    private final cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService treeScopes;

    public TaskCommandResult act(TaskActionCommand command, TaskWorkbenchActor actor) {
        AtomicReference<ActionFacts> facts = new AtomicReference<>();
        try {
            validate(command, actor);
            String action = command.actionCode().toUpperCase(java.util.Locale.ROOT);
            var execution = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                            actor.tenantId(), COMMAND_SCOPE + ":" + action, actor.actorId(), command.idempotencyKey()),
                    command.requestDigest(), TaskCommandResult.class,
                    () -> actOnce(command, action, actor, facts),
                    result -> successFacts(result, action, actor, facts.get()));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) {
                throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
            }
            if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS
                    || execution.response() == null) {
                throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
            }
            TaskCommandResult result = execution.response();
            return execution.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED
                    ? new TaskCommandResult(result.taskId(), result.taskVersion(), result.taskTreeVersion(),
                    result.status(), "REPLAY_COMPLETED") : result;
        } catch (RuntimeException ex) {
            auditRejected(command, actor, ex);
            throw ex;
        }
    }

    private TaskCommandResult actOnce(TaskActionCommand command, String action, TaskWorkbenchActor actor,
                                      AtomicReference<ActionFacts> factsRef) {
        ProjectTaskInstanceDO initial = taskMapper.selectTask(new TaskByIdQuery(actor.tenantId(), command.taskId()));
        if (initial == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        ProjectMasterDO project = taskMapper.selectProjectForCommandForUpdate(
                new ProjectTaskProjectLockQuery(actor.tenantId(), initial.getProjectId()));
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())
                || !"ACTIVE".equals(project.getLifecycleStatus())) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        ProjectTaskInstanceDO task = taskMapper.selectTaskForAssignmentForUpdate(
                new TaskAssignmentCommandQuery(actor.tenantId(), project.getId(), command.taskId()));
        if (task == null) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        if (!Objects.equals(task.getVersion(), command.expectedTaskVersion())) {
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        ProjectTaskExecutionContractDO contract = requireCurrentContract(task, actor.tenantId());
        if ("APPROVAL".equals(contract.getWorkBindingTypeCode()) && Set.of("START", "APPROVAL").contains(action)) {
            if (!Objects.equals(command.executionContractId(), contract.getId())
                    || !Objects.equals(command.contractVersion(), contract.getContractVersion()))
                throw exception(PROJECT_TASK_VERSION_CONFLICT);
        } else if (command.approval() != null || "APPROVAL".equals(action)) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        if ("APPROVAL".equals(action)) return submitApproval(command, actor, project, task, contract, factsRef);
        TaskStateTransitionDO transition;
        // 指派命令使用相同的项目→任务锁顺序；锁内事实决定是否允许省略指派前置。
        boolean designated = "START".equals(action) && assignmentMapper.selectCurrentForUpdate(
                new TaskAssignmentLockQuery(actor.tenantId(), task.getId())) != null;
        String transitionSource = TaskExecutionPolicy.transitionSource(task.getStatus(), action, designated);
        try {
            transition = stateMachineMapper.requireTransition(new TaskStateTransitionQuery(actor.tenantId(),
                    task.getStateMachineRevisionId(), transitionSource, action));
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        boolean acceptanceContract = isAcceptanceContract(contract);
        if (acceptanceContract) {
            requireAcceptanceActionAccess(action, task, actor);
        } else {
            TaskBindingInspectionQuery query = new TaskBindingInspectionQuery(
                    actor.tenantId(), task.getId(), actor.actorId(), actor.correlationId());
            TaskBindingInspection inspection = isBusinessContract(contract)
                    ? businessProvider.inspect(query) : nativeProvider.inspect(query);
            if (inspection.recoverableError() != null || !inspection.allowedActions().contains(action)) {
                throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            }
        }
        LocalDateTime occurredAt = LocalDateTime.now();
        requireCurrentSubject(action, task, actor, occurredAt, acceptanceContract);
        if ("START".equals(action) && !stageAdmissionService.taskMayStart(project, task, contract))
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        if ("CANCEL".equals(action) && "APPROVAL".equals(contract.getWorkBindingTypeCode()))
            taskApprovals.requireMayCancel(actor.tenantId(), project.getId(), task.getId(), contract);
        CompletionDecision completion = "COMPLETE".equals(action)
                ? acceptanceContract
                ? completeAcceptance(command, task, contract, actor, occurredAt)
                : evaluateCompletion(command, project, task, contract, actor, occurredAt)
                : CompletionDecision.notApplicable();
        if ("COMPLETE".equals(action)) insertEvaluation(command, task, contract, actor, occurredAt, completion);
        if (!completion.satisfied()) {
            ActionFacts facts = ActionFacts.evaluated(task, contract, completion, occurredAt);
            factsRef.set(facts);
            return new TaskCommandResult(task.getId(), task.getVersion(), project.getTaskTreeVersion(),
                    task.getStatus(), "NEW");
        }
        var result = applyTransition(project, task, contract, action, transition, completion, actor,
                occurredAt, command.reason());
        if ("START".equals(action) && "APPROVAL".equals(contract.getWorkBindingTypeCode()))
            taskApprovals.start(actor.tenantId(), project.getId(), task.getId(), contract, actor.actorId(),
                    "START:" + command.idempotencyKey(), command.approval());
        factsRef.set(ActionFacts.changed(task, contract, completion, occurredAt, transitionSource));
        return result;
    }

    /** A business submission inside an already-started task, not a second task START/state transition. */
    private TaskCommandResult submitApproval(TaskActionCommand command, TaskWorkbenchActor actor,
            ProjectMasterDO project, ProjectTaskInstanceDO task, ProjectTaskExecutionContractDO contract,
            AtomicReference<ActionFacts> factsRef) {
        if (!"IN_PROGRESS".equals(task.getStatus())) throw exception(PROJECT_TASK_COMMAND_INVALID);
        var inspection = nativeProvider.inspect(new TaskBindingInspectionQuery(actor.tenantId(), task.getId(),
                actor.actorId(), actor.correlationId()));
        if (inspection.recoverableError() != null || !inspection.allowedActions().contains("APPROVAL"))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var now = LocalDateTime.now();
        requireCurrentSubject("START", task, actor, now, false);
        var receipt = taskApprovals.start(actor.tenantId(), project.getId(), task.getId(), contract, actor.actorId(),
                "APPROVAL:" + command.idempotencyKey(), command.approval());
        var facts = new CompletionDecision(true, List.of(), null, null, now, Map.of("approval", receipt));
        factsRef.set(ActionFacts.evaluated(task, contract, facts, now));
        return new TaskCommandResult(task.getId(), task.getVersion(), project.getTaskTreeVersion(), task.getStatus(), "NEW");
    }

    private TaskCommandResult applyTransition(ProjectMasterDO project, ProjectTaskInstanceDO task,
            ProjectTaskExecutionContractDO contract, String action, TaskStateTransitionDO transition,
            CompletionDecision completion, TaskWorkbenchActor actor, LocalDateTime occurredAt, String reason) {
        String nextStatus = transition.getToStatusCode();
        Integer progress = "SUBMIT".equals(action) ? Integer.valueOf(99)
                : "COMPLETE".equals(action) ? Integer.valueOf(100) : null;
        if (taskMapper.updateLifecycleIfMatch(new TaskLifecycleStateUpdate(actor.tenantId(), project.getId(),
                task.getId(), task.getVersion(), task.getStatus(), nextStatus, "START".equals(action),
                "COMPLETE".equals(action) || "CANCEL".equals(action), progress, occurredAt,
                String.valueOf(actor.actorId()))) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        Map<String, Object> roundEvidence = new LinkedHashMap<>();
        roundEvidence.put("action", action); roundEvidence.put("taskVersion", task.getVersion() + 1);
        roundEvidence.put("contractId", contract.getId()); roundEvidence.put("correlationId", actor.correlationId());
        if (completion != null) roundEvidence.put("completionEvaluationId", completion.evaluationId());
        if (completion != null && "COMPLETE".equals(action)) {
            roundEvidence.put("completion", completion.businessEvidence().get("completion"));
            roundEvidence.put("exit", completion.businessEvidence().get("exit"));
            roundEvidence.put("gate", completion.businessEvidence().get("gate"));
            roundEvidence.put("gateSnapshot", completion.businessEvidence().get("gateSnapshot"));
            roundEvidence.put("businessFacts", completion.businessEvidence().get("businessFacts"));
            roundEvidence.put("approval", completion.businessEvidence().get("approval"));
            if (completion.businessEvidence().containsKey("subscriptionEvidence")) {
                roundEvidence.put("subscription",completion.businessEvidence().get("subscription"));
                roundEvidence.put("subscriptionEvidence",completion.businessEvidence().get("subscriptionEvidence"));
            }
        }
        if ("CANCEL".equals(action)) roundEvidence.put("reason", reason);
        if (nodeExecutions.recordTaskTransition(new cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper.TaskTransition(
                actor.tenantId(), project.getId(), task.getId(), contract.getId(), action, occurredAt, actor.actorId(),
                JsonUtils.toJsonString(roundEvidence))) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        if (Set.of("START", "COMPLETE").contains(action)) timers.scheduleFromNode(project.getId(), "TASK", task.getId());
        if (Set.of("SUBMIT", "COMPLETE", "CANCEL").contains(action)) {
            progressService.recompute(actor.tenantId(), project.getId(), project.getTaskProgressVersion(), occurredAt);
        }
        return new TaskCommandResult(task.getId(), task.getVersion() + 1, project.getTaskTreeVersion(),
                nextStatus, "NEW");
    }

    /** Called under the admission command's project/task locks after its frozen rule matched. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY, rollbackFor = Exception.class)
    public boolean startAdmittedTask(ProjectMasterDO project, ProjectTaskInstanceDO task,
            ProjectTaskExecutionContractDO contract, String correlationId) {
        if (!Set.of("PENDING_ASSIGN", "PENDING_START").contains(task.getStatus())) return false;
        boolean designated = assignmentMapper.selectCurrentForUpdate(
                new TaskAssignmentLockQuery(project.getTenantId(), task.getId())) != null;
        String source = TaskExecutionPolicy.transitionSource(task.getStatus(), "START", designated);
        var transition = stateMachineMapper.requireTransition(new TaskStateTransitionQuery(project.getTenantId(),
                task.getStateMachineRevisionId(), source, "START"));
        var actor = new TaskWorkbenchActor(project.getTenantId(), 0L,
                correlationId == null || correlationId.isBlank() ? "project-rules:" + project.getId() : correlationId);
        var result = applyTransition(project, task, contract, "START", transition,
                CompletionDecision.notApplicable(), actor, LocalDateTime.now(), null);
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), "PROJECT_TASK_START",
                "ProjectTask", task.getId().toString(), "SUCCESS", Map.of("executionMode", "AUTOMATIC",
                        "contractId", contract.getId(), "fromStatus", task.getStatus(), "toStatus", result.status()));
        return true;
    }

    public record AutomaticResult(boolean completed, boolean unknown) { }

    /** Internal rule command, not exposed by the user-action controller. No simulated user/session. */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public AutomaticResult completeFromBusinessResult(Long projectId, Long taskId, String correlationId) {
        Long tenantId = cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId();
        var project = taskMapper.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenantId, projectId));
        if (project == null || !"ACTIVE".equals(project.getLifecycleStatus())) return new AutomaticResult(false, false);
        var task = taskMapper.selectTaskForAssignmentForUpdate(new TaskAssignmentCommandQuery(tenantId, projectId, taskId));
        if (task == null || !Set.of("IN_PROGRESS", "PENDING_ACCEPT").contains(task.getStatus()))
            return new AutomaticResult(false, false);
        var contract = requireCurrentContract(task, tenantId);
        boolean nativeWork = TaskNativeCompletionPolicy.WORK_BINDING_TYPE.equals(contract.getWorkBindingTypeCode());
        if ((!isBusinessContract(contract) && !nativeWork && !"APPROVAL".equals(contract.getWorkBindingTypeCode())
                && !"RESULT_SUBSCRIPTION".equals(contract.getWorkBindingTypeCode()))
                || isAcceptanceContract(contract)) return new AutomaticResult(false, false);
        if (nativeWork && !"PENDING_ACCEPT".equals(task.getStatus())) return new AutomaticResult(false, false);
        var evaluated = planCompletion.evaluateAutomatically(project, task, contract);
        var now = LocalDateTime.now();
        var decision = guardCompletion(evaluated, task, tenantId, now);
        if (!decision.satisfied()) return new AutomaticResult(false,
                "UNKNOWN".equals(completionResult(false, decision.businessEvidence())));

        // Traverse the task's frozen state machine atomically; no user confirmation or extra approval is introduced.
        String from = task.getStatus();
        List<String> path = new ArrayList<>();
        if ("IN_PROGRESS".equals(from)) {
            from = stateMachineMapper.requireTransition(new TaskStateTransitionQuery(tenantId,
                    task.getStateMachineRevisionId(), from, "SUBMIT")).getToStatusCode();
            path.add("SUBMIT");
        }
        var transition = stateMachineMapper.requireTransition(new TaskStateTransitionQuery(tenantId,
                task.getStateMachineRevisionId(), from, "COMPLETE"));
        if (!"DONE".equals(transition.getToStatusCode())) throw exception(PROJECT_TASK_COMMAND_INVALID);
        path.add("COMPLETE");
        var evidence = new LinkedHashMap<>(decision.businessEvidence());
        evidence.put("executionMode", "AUTOMATIC"); evidence.put("transitionPath", List.copyOf(path));
        var completion = new CompletionDecision(true, List.of(), decision.evaluationId(), decision.gateSnapshot(), now,
                Collections.unmodifiableMap(evidence));
        String key = "rule-complete:" + evidence.get("executionId");
        if (!(evidence.get("executionId") instanceof Long)) throw exception(PROJECT_TASK_COMMAND_INVALID);
        // 0 is the platform system actor convention; actor identity is not used as a business permission grant.
        var actor = new TaskWorkbenchActor(tenantId, 0L,
                correlationId == null || correlationId.isBlank() ? "project-rules:" + projectId : correlationId);
        var result = commandExecutionApi.execute(new PlatformCommandExecutionApi.IdempotencyScope(
                        tenantId, "PROJECT_TASK_RULE_COMPLETE", 0L, key), cn.hutool.crypto.digest.DigestUtil.sha256Hex(key),
                TaskCommandResult.class, () -> {
                    insertEvaluation(key, task, contract, actor, now, completion);
                    return applyTransition(project, task, contract, "COMPLETE", transition, completion, actor, now, null);
                }, completed -> successFacts(completed, "COMPLETE", actor,
                        ActionFacts.changed(task, contract, completion, now, task.getStatus())));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT
                || result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS)
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        return new AutomaticResult(result.decision() == PlatformCommandExecutionApi.Decision.NEW, false);
    }

    private ProjectTaskExecutionContractDO requireCurrentContract(ProjectTaskInstanceDO task, Long tenantId) {
        ProjectTaskExecutionContractDO contract = contractMapper.selectCurrentByTaskIdForUpdate(
                new CurrentTaskExecutionContractLockQuery(tenantId, task.getId()));
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)
                || (!TaskNativeCompletionPolicy.WORK_BINDING_TYPE.equals(contract.getWorkBindingTypeCode())
                && !"APPROVAL".equals(contract.getWorkBindingTypeCode())
                && !isAcceptanceContract(contract) && !isBusinessContract(contract)
                && !"RESULT_SUBSCRIPTION".equals(contract.getWorkBindingTypeCode()))) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        // 订阅合同只接受编译器生成的查询投影，不能借新类型夹带Owner或人工办理权限。
        if ("RESULT_SUBSCRIPTION".equals(contract.getWorkBindingTypeCode()))
            cn.iocoder.yudao.module.pms.project.domain.template.ResultSubscriptionTaskContract.requireRuntime(contract);
        return contract;
    }

    private static boolean isBusinessContract(ProjectTaskExecutionContractDO contract) {
        return contract != null && TaskBusinessBindingHostProvider.TYPES.contains(contract.getWorkBindingTypeCode());
    }

    private static boolean isAcceptanceContract(ProjectTaskExecutionContractDO contract) {
        return contract != null && "ACC".equals(contract.getTargetContextCode())
                && "AcceptanceActivity".equals(contract.getTargetObjectType())
                && contract.getTargetObjectKey() != null && !contract.getTargetObjectKey().isBlank();
    }

    private void requireAcceptancePermissions(Long actorId) {
        if (!permissionApi.hasAnyPermissions(actorId, "pms:project-task:execute")
                || !permissionApi.hasAnyPermissions(actorId, "pms:acceptance:report:complete")) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
    }

    private void requireAcceptanceActionAccess(String action, ProjectTaskInstanceDO task,
                                               TaskWorkbenchActor actor) {
        if ("START".equals(action) || "SUBMIT".equals(action)) {
            requirePermission(actor.actorId(), "pms:project-task:execute");
            requireProjectScope(task, actor, ProjectScopeApi.ACTION_EDIT);
            return;
        }
        if ("COMPLETE".equals(action)) {
            requireAcceptancePermissions(actor.actorId());
            requireProjectScope(task, actor, ProjectScopeApi.ACTION_MANAGE);
            return;
        }
        throw exception(PROJECT_TASK_COMMAND_INVALID);
    }

    private void requirePermission(Long actorId, String permission) {
        if (!permissionApi.hasAnyPermissions(actorId, permission)) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
    }

    private void requireProjectScope(ProjectTaskInstanceDO task, TaskWorkbenchActor actor, String action) {
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), task.getProjectId(), action));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(task.getProjectId())) {
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        }
    }

    private CompletionDecision completeAcceptance(TaskActionCommand command, ProjectTaskInstanceDO task,
                                                  ProjectTaskExecutionContractDO contract,
                                                  TaskWorkbenchActor actor, LocalDateTime occurredAt) {
        if (!Objects.equals(command.executionContractId(), contract.getId())
                || !Objects.equals(command.contractVersion(), contract.getContractVersion())
                || command.expectedActivityVersion() == null || command.expectedActivityVersion() < 0
                || command.expectedReportVersion() == null || command.expectedReportVersion() <= 0
                || !Objects.equals(command.factObjectKey(), contract.getTargetObjectKey())) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        Long acceptanceId;
        try {
            acceptanceId = Long.valueOf(contract.getTargetObjectKey());
        } catch (NumberFormatException ex) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        var fact = acceptanceActivityCompletionFactApi.lockAndComplete(
                new AcceptanceActivityCompletionCommand(actor.tenantId(), task.getProjectId(), task.getId(),
                        task.getVersion(), contract.getId(), acceptanceId, command.expectedActivityVersion(),
                        command.expectedReportVersion(), command.idempotencyKey()));
        if (fact == null || "DEPENDENCY_UNAVAILABLE".equals(fact.outcome())) {
            throw exception(ACC_REPORT_DEPENDENCY_UNAVAILABLE);
        }
        if ("REPORT_INCOMPLETE".equals(fact.outcome())) throw exception(ACC_REPORT_INCOMPLETE);
        if ("VERSION_CONFLICT".equals(fact.outcome())) throw exception(ACC_REPORT_VERSION_CONFLICT);
        if (!"COMPLETED".equals(fact.outcome()) || !Objects.equals(fact.acceptanceId(), acceptanceId)) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        return new CompletionDecision(true, List.of(), IdWorker.getId(),
                "ACC:" + fact.acceptanceId() + ":" + fact.activityVersion()
                        + ":REPORT:" + fact.reportVersionId() + ":" + fact.reportVersion(), occurredAt);
    }

    private void requireCurrentSubject(String action, ProjectTaskInstanceDO task,
                                       TaskWorkbenchActor actor, LocalDateTime effectiveAt,
                                       boolean acceptanceContract) {
        if (treeScopes.isTenantSuperAdmin(actor.tenantId(), actor.actorId())) {
            // 超管授权不创建指派事实；未指定任务不再要求存在空的指派前置。
            return;
        }
        if ("START".equals(action) || "SUBMIT".equals(action)) {
            var assignment = assignmentMapper.selectCurrentForUpdate(new TaskAssignmentLockQuery(actor.tenantId(), task.getId()));
            var memberships = memberMapper.selectActiveByUserForUpdate(new ActiveProjectMemberForUpdateQuery(
                    actor.tenantId(), task.getProjectId(), actor.actorId(), effectiveAt));
            if (!TaskExecutionPolicy.permits(task.getProjectId(), actor.actorId(), assignment, memberships)) {
                throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            }
            return;
        }
        boolean projectManager = memberMapper.selectActiveByUserForUpdate(
                        new ActiveProjectMemberForUpdateQuery(actor.tenantId(), task.getProjectId(),
                                actor.actorId(), effectiveAt)).stream()
                .map(ProjectMemberAssignmentDO::getMemberRole)
                .anyMatch("PROJECT_MANAGER"::equals);
        if (acceptanceContract && "COMPLETE".equals(action)) {
            if (!projectManager && !isCurrentAssignee(task, actor)) {
                throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            }
            return;
        }
        if (!projectManager) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
    }

    private boolean isCurrentAssignee(ProjectTaskInstanceDO task, TaskWorkbenchActor actor) {
        var assignment = assignmentMapper.selectCurrentForUpdate(
                new TaskAssignmentLockQuery(actor.tenantId(), task.getId()));
        return assignment != null && Objects.equals(assignment.getAssigneeUserId(), actor.actorId());
    }

    private CompletionDecision evaluateCompletion(TaskActionCommand command, ProjectMasterDO project, ProjectTaskInstanceDO task,
                                                   ProjectTaskExecutionContractDO contract,
                                                   TaskWorkbenchActor actor, LocalDateTime occurredAt) {
        var result = planCompletion.evaluate(command, project, task, contract, actor);
        var decision = guardCompletion(result, task, actor.tenantId(), occurredAt);
        if (!isBusinessContract(contract) && (command.factObjectKey() != null || command.factVersion() != null)
                && (!String.valueOf(task.getId()).equals(command.factObjectKey())
                || !Objects.equals(Long.valueOf(task.getVersion()), command.factVersion()))) {
            var unmet = new ArrayList<>(decision.unmetItems()); unmet.add("TASK_FACT_VERSION_MISMATCH");
            return new CompletionDecision(false, List.copyOf(unmet), decision.evaluationId(), decision.gateSnapshot(),
                    occurredAt, decision.businessEvidence());
        }
        return decision;
    }

    private CompletionDecision guardCompletion(ProjectTaskPlanCompletionService.Result result, ProjectTaskInstanceDO task,
            Long tenantId, LocalDateTime occurredAt) {
        List<String> unmet = new ArrayList<>(result.unmet());
        if (task.getName() == null || task.getName().isBlank() || task.getStageCode() == null
                || task.getStageCode().isBlank()) unmet.add("TASK_REQUIRED_FACT_MISSING");
        TaskCompletionFactsQuery query = new TaskCompletionFactsQuery(tenantId, task.getProjectId(), task.getId());
        if (!taskMapper.selectUnfinishedStartedDescendantIdsForUpdate(query).isEmpty()) {
            unmet.add("UNFINISHED_STARTED_DESCENDANT");
        }
        // The effective plan evaluator rechecks the gate in this transaction, including changed gate references.
        String gateSnapshot = (String) result.evidence().get("gateSnapshot");
        return new CompletionDecision(result.matched() && unmet.isEmpty(), List.copyOf(unmet), IdWorker.getId(), gateSnapshot,
                occurredAt, result.evidence());
    }

    private void insertEvaluation(TaskActionCommand command, ProjectTaskInstanceDO task,
                                  ProjectTaskExecutionContractDO contract, TaskWorkbenchActor actor,
                                  LocalDateTime occurredAt, CompletionDecision decision) {
        insertEvaluation(command.idempotencyKey(), task, contract, actor, occurredAt, decision);
    }

    private void insertEvaluation(String commandKey, ProjectTaskInstanceDO task,
                                  ProjectTaskExecutionContractDO contract, TaskWorkbenchActor actor,
                                  LocalDateTime occurredAt, CompletionDecision decision) {
        ProjectTaskCompletionEvaluationDO evaluation = new ProjectTaskCompletionEvaluationDO();
        evaluation.setId(decision.evaluationId());
        evaluation.setTenantId(actor.tenantId());
        evaluation.setProjectTaskId(task.getId());
        evaluation.setExecutionContractId(contract.getId());
        evaluation.setTaskVersion(task.getVersion());
        evaluation.setContractVersion(contract.getContractVersion());
        evaluation.setEvaluationResultCode(completionResult(decision.satisfied(),decision.businessEvidence()));
        evaluation.setUnmetItemsJson(JsonUtils.toJsonString(decision.unmetItems()));
        evaluation.setCommandId(commandKey);
        evaluation.setIdempotencyKey(commandKey);
        evaluation.setFactContextCode("PROJ");
        evaluation.setFactObjectType("ProjectTask");
        evaluation.setFactObjectKey(String.valueOf(task.getId()));
        evaluation.setFactVersion((long) task.getVersion());
        evaluation.setBusinessFactsJson(JsonUtils.toJsonString(decision.businessEvidence()));
        if (isBusinessContract(contract) && !isAcceptanceContract(contract)) {
            evaluation.setFactContextCode(contract.getTargetContextCode());
            evaluation.setFactObjectType(contract.getTargetObjectType());
            // A group is not a ProjectTask fact. Exact identities and opaque Owner versions live in JSON.
            evaluation.setFactObjectKey(null);
            evaluation.setFactVersion(null);
            evaluation.setBusinessFactsJson(JsonUtils.toJsonString(decision.businessEvidence()));
        }
        evaluation.setGateSnapshotRef(decision.gateSnapshot());
        evaluation.setEvaluatedBy(actor.actorId());
        evaluation.setEvaluatedAt(occurredAt);
        evaluation.setVersion(0);
        evaluation.setCreator(String.valueOf(actor.actorId()));
        evaluation.setUpdater(String.valueOf(actor.actorId()));
        if (evaluationMapper.insertEvaluation(evaluation) != 1) {
            throw new IllegalStateException("PROJECT_TASK_COMPLETION_EVALUATION_WRITE_FAILED");
        }
    }

    private PlatformCommandExecutionApi.SuccessFacts successFacts(TaskCommandResult result, String action,
                                                                  TaskWorkbenchActor actor, ActionFacts facts) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("projectId", facts.projectId());
        detail.put("projectTaskId", result.taskId());
        detail.put("action", action);
        detail.put("beforeStatus", facts.beforeStatus());
        detail.put("afterStatus", result.status());
        detail.put("stateMachineRevisionId", facts.stateMachineRevisionId());
        detail.put("transitionFromStatus", facts.transitionFromStatus());
        detail.put("executionContractId", facts.contractId());
        detail.put("contractVersion", facts.contractVersion());
        if (facts.evaluationId() != null) {
            detail.put("completionEvaluationId", facts.evaluationId());
            detail.put("completionResult", completionResult(facts.completionSatisfied(),facts.businessEvidence()));
            detail.put("unmetItems", facts.unmetItems());
        }
        if (!facts.businessEvidence().isEmpty()) detail.put(facts.businessWork() ? "businessFacts" : "ruleEvaluation", facts.businessEvidence());
        String eventType = "COMPLETE".equals(action) && facts.completionSatisfied() ? "TaskCompleted" : null;
        String eventPayload = eventType == null ? null : JsonUtils.toJsonString(new TaskCompletedMessage.Payload(
                actor.tenantId(), facts.projectId(), result.taskId(), facts.evaluationId(), result.taskVersion(),
                facts.contractId(), facts.contractVersion(),
                facts.businessWork() ? null : (long) facts.beforeTaskVersion(), actor.actorId(),
                facts.occurredAt(), facts.businessWork() ? facts.businessEvidence() : null));
        return new PlatformCommandExecutionApi.SuccessFacts("PROJECT_TASK_" + action, "ProjectTask",
                String.valueOf(result.taskId()), actor.correlationId(), JsonUtils.toJsonString(detail),
                eventType, eventPayload, List.of(
                        new cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleReevaluation(
                                actor.tenantId(), facts.projectId(), actor.actorId(), actor.correlationId())
                                .event()));
    }

    private void validate(TaskActionCommand command, TaskWorkbenchActor actor) {
        String action = command == null || command.actionCode() == null ? null
                : command.actionCode().toUpperCase(java.util.Locale.ROOT);
        if (command == null || command.taskId() == null || command.taskId() <= 0
                || command.expectedTaskVersion() == null || command.expectedTaskVersion() < 0
                || !ACTIONS.contains(action) || ("CANCEL".equals(action)
                && (command.reason() == null || command.reason().isBlank()))
                || ("COMPLETE".equals(action) && (command.executionContractId() == null
                || command.contractVersion() == null))
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.requestDigest() == null || !command.requestDigest().matches("[0-9a-f]{64}")
                || actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null || actor.correlationId().isBlank()) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
    }

    private void auditRejected(TaskActionCommand command, TaskWorkbenchActor actor, RuntimeException ex) {
        if (actor == null || actor.tenantId() == null || actor.actorId() == null
                || actor.correlationId() == null) return;
        Map<String, Object> detail = new LinkedHashMap<>();
        if (command != null && command.taskId() != null) detail.put("projectTaskId", command.taskId());
        if (command != null && command.actionCode() != null) detail.put("action", command.actionCode());
        boolean approvalSubmission = command != null && (command.approval() != null
                || "APPROVAL".equalsIgnoreCase(command.actionCode()));
        detail.put("failureCode", ex instanceof ServiceException service ? String.valueOf(service.getCode())
                : approvalSubmission ? "TASK_APPROVAL_ACTION_FAILED"
                : ex.getMessage() == null ? "PROJECT_TASK_ACTION_FAILED" : ex.getMessage());
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "PROJECT_TASK_ACTION", "ProjectTask",
                command == null || command.taskId() == null ? "UNKNOWN" : String.valueOf(command.taskId()),
                "REJECTED", Collections.unmodifiableMap(detail));
    }

    private record CompletionDecision(boolean satisfied, List<String> unmetItems, Long evaluationId,
                                      String gateSnapshot, LocalDateTime occurredAt,
                                      Map<String, Object> businessEvidence) {
        CompletionDecision(boolean satisfied, List<String> unmetItems, Long evaluationId,
                           String gateSnapshot, LocalDateTime occurredAt) {
            this(satisfied, unmetItems, evaluationId, gateSnapshot, occurredAt, Map.of());
        }
        static CompletionDecision notApplicable() {
            return new CompletionDecision(true, List.of(), null, null, null);
        }
    }

    private static String completionResult(boolean matched, Map<String,Object> evidence) {
        boolean unknown = java.util.stream.Stream.of("completion", "exit", "gate").map(evidence::get)
                .filter(cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.class::isInstance)
                .map(cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.class::cast)
                .anyMatch(result -> result.outcome() == cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation.Outcome.UNKNOWN);
        return unknown ? "UNKNOWN" : matched ? "SATISFIED" : "NOT_SATISFIED";
    }

    private record ActionFacts(Long projectId, String beforeStatus, int beforeTaskVersion,
                               Long stateMachineRevisionId, Long contractId, Integer contractVersion,
                               Long evaluationId, boolean completionSatisfied, List<String> unmetItems,
                               LocalDateTime occurredAt, Map<String, Object> businessEvidence,
                               String transitionFromStatus, boolean businessWork) {
        static ActionFacts changed(ProjectTaskInstanceDO task, ProjectTaskExecutionContractDO contract,
                                   CompletionDecision completion, LocalDateTime occurredAt, String transitionSource) {
            return create(task, contract, completion, occurredAt, transitionSource);
        }
        static ActionFacts evaluated(ProjectTaskInstanceDO task, ProjectTaskExecutionContractDO contract,
                                     CompletionDecision completion, LocalDateTime occurredAt) {
            return create(task, contract, completion, occurredAt, task.getStatus());
        }
        private static ActionFacts create(ProjectTaskInstanceDO task, ProjectTaskExecutionContractDO contract,
                                          CompletionDecision completion, LocalDateTime occurredAt, String transitionSource) {
            return new ActionFacts(task.getProjectId(), task.getStatus(), task.getVersion(),
                    task.getStateMachineRevisionId(), contract.getId(), contract.getContractVersion(),
                    completion.evaluationId(), completion.satisfied(), completion.unmetItems(), occurredAt,
                    completion.businessEvidence(), transitionSource, isBusinessContract(contract) && !isAcceptanceContract(contract));
        }
    }
}
