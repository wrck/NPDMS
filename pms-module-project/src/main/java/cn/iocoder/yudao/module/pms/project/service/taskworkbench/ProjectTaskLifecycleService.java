package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskCompletionEvaluationDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateForUpdateQuery;
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
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.event.TaskCompletedMessage;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.AcceptanceActivityCompletionFactApi;
import cn.iocoder.yudao.module.pms.project.api.acceptanceactivity.dto.AcceptanceActivityCompletionCommand;
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

/** TASK_NATIVE动作、完成判定、审计与Outbox应用服务。 */
@Service
@RequiredArgsConstructor
public class ProjectTaskLifecycleService {

    private static final Set<String> ACTIONS = Set.of("START", "SUBMIT", "COMPLETE", "CANCEL");
    private static final String COMMAND_SCOPE = "POST:/api/v1/pms/project-tasks/{id}/actions/{action}";

    private final ProjectTaskRuntimeMapper taskMapper;
    private final ProjectTaskExecutionContractMapper contractMapper;
    private final ProjectTaskAssignmentMapper assignmentMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTaskCompletionEvaluationMapper evaluationMapper;
    private final ProjectGateInstanceMapper gateMapper;
    private final TaskStateMachineMapper stateMachineMapper;
    private final TaskNativeBindingHostProvider nativeProvider;
    private final PlatformCommandExecutionApi commandExecutionApi;
    private final OperationAuditApi operationAuditApi;
    private final ProjectTaskProgressService progressService;
    private final PermissionApi permissionApi;
    private final AcceptanceActivityCompletionFactApi acceptanceActivityCompletionFactApi;
    private final ProjectScopeApi projectScopeApi;

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
        TaskStateTransitionDO transition;
        try {
            transition = stateMachineMapper.requireTransition(new TaskStateTransitionQuery(actor.tenantId(),
                    task.getStateMachineRevisionId(), task.getStatus(), action));
        } catch (IllegalArgumentException ex) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        ProjectTaskExecutionContractDO contract = requireCurrentContract(task, actor.tenantId());
        boolean acceptanceContract = isAcceptanceContract(contract);
        if (acceptanceContract) {
            requireAcceptanceActionAccess(action, task, actor);
        } else {
            TaskBindingInspection inspection = nativeProvider.inspect(new TaskBindingInspectionQuery(
                    actor.tenantId(), task.getId(), actor.actorId(), actor.correlationId()));
            if (inspection.recoverableError() != null || !inspection.allowedActions().contains(action)) {
                throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
            }
        }
        LocalDateTime occurredAt = LocalDateTime.now();
        requireCurrentSubject(action, task, actor, occurredAt, acceptanceContract);
        CompletionDecision completion = "COMPLETE".equals(action)
                ? acceptanceContract
                ? completeAcceptance(command, task, contract, actor, occurredAt)
                : evaluateCompletion(command, task, contract, actor, occurredAt)
                : CompletionDecision.notApplicable();
        if ("COMPLETE".equals(action)) insertEvaluation(command, task, contract, actor, occurredAt, completion);
        if (!completion.satisfied()) {
            ActionFacts facts = ActionFacts.evaluated(task, contract, completion, occurredAt);
            factsRef.set(facts);
            return new TaskCommandResult(task.getId(), task.getVersion(), project.getTaskTreeVersion(),
                    task.getStatus(), "NEW");
        }
        String nextStatus = transition.getToStatusCode();
        Integer progress = "SUBMIT".equals(action) ? Integer.valueOf(99)
                : "COMPLETE".equals(action) ? Integer.valueOf(100) : null;
        if (taskMapper.updateLifecycleIfMatch(new TaskLifecycleStateUpdate(actor.tenantId(), project.getId(),
                task.getId(), task.getVersion(), task.getStatus(), nextStatus, "START".equals(action),
                "COMPLETE".equals(action) || "CANCEL".equals(action), progress, occurredAt,
                String.valueOf(actor.actorId()))) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        if (Set.of("SUBMIT", "COMPLETE", "CANCEL").contains(action)) {
            progressService.recompute(actor.tenantId(), project.getId(), project.getTaskProgressVersion(), occurredAt);
        }
        ActionFacts facts = ActionFacts.changed(task, contract, completion, occurredAt);
        factsRef.set(facts);
        return new TaskCommandResult(task.getId(), task.getVersion() + 1, project.getTaskTreeVersion(),
                nextStatus, "NEW");
    }

    private ProjectTaskExecutionContractDO requireCurrentContract(ProjectTaskInstanceDO task, Long tenantId) {
        ProjectTaskExecutionContractDO contract = contractMapper.selectCurrentByTaskIdForUpdate(
                new CurrentTaskExecutionContractLockQuery(tenantId, task.getId()));
        if (contract == null || !Objects.equals(contract.getTenantId(), tenantId)
                || (!"TASK_NATIVE".equals(contract.getWorkBindingTypeCode()) && !isAcceptanceContract(contract))) {
            throw exception(PROJECT_TASK_COMMAND_INVALID);
        }
        return contract;
    }

    private boolean isAcceptanceContract(ProjectTaskExecutionContractDO contract) {
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
        if ("START".equals(action) || "SUBMIT".equals(action)) {
            if (!isCurrentAssignee(task, actor)) {
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

    private CompletionDecision evaluateCompletion(TaskActionCommand command, ProjectTaskInstanceDO task,
                                                   ProjectTaskExecutionContractDO contract,
                                                   TaskWorkbenchActor actor, LocalDateTime occurredAt) {
        List<String> unmet = new ArrayList<>();
        if (!Objects.equals(command.executionContractId(), contract.getId())
                || !Objects.equals(command.contractVersion(), contract.getContractVersion())) {
            unmet.add("EXECUTION_CONTRACT_VERSION_MISMATCH");
        }
        Map<?, ?> rule = JsonUtils.parseObject(contract.getCompletionRuleSnapshot(), Map.class);
        if (rule == null || !"DONE".equals(rule.get("requiredStatus"))) unmet.add("COMPLETION_RULE_INVALID");
        if (task.getName() == null || task.getName().isBlank() || task.getStageCode() == null
                || task.getStageCode().isBlank()) unmet.add("TASK_REQUIRED_FACT_MISSING");
        TaskCompletionFactsQuery query = new TaskCompletionFactsQuery(actor.tenantId(), task.getProjectId(), task.getId());
        if (!taskMapper.selectNonTerminalDescendantIdsForUpdate(query).isEmpty()) {
            unmet.add("NON_TERMINAL_DESCENDANT");
        }
        if (!taskMapper.selectNonTerminalPredecessorIdsForUpdate(query).isEmpty()) {
            unmet.add("NON_TERMINAL_PREDECESSOR");
        }
        String gateSnapshot = null;
        if (contract.getGateRef() != null && !contract.getGateRef().isBlank()) {
            ProjectGateInstanceDO gate = gateMapper.selectByCodeForUpdate(new ProjectGateForUpdateQuery(
                    actor.tenantId(), task.getProjectId(), contract.getGateRef()));
            gateSnapshot = contract.getGateRef() + ":" + (gate == null ? "UNKNOWN" : gate.getStatus())
                    + ":" + (gate == null ? "" : gate.getVersion());
            if (gate == null || !"PASSED".equals(gate.getStatus())) unmet.add("GATE_NOT_PASSED");
        }
        if (command.factObjectKey() != null || command.factVersion() != null) {
            if (!String.valueOf(task.getId()).equals(command.factObjectKey())
                    || !Objects.equals(Long.valueOf(task.getVersion()), command.factVersion())) {
                unmet.add("TASK_FACT_VERSION_MISMATCH");
            }
        }
        return new CompletionDecision(unmet.isEmpty(), List.copyOf(unmet), IdWorker.getId(), gateSnapshot,
                occurredAt);
    }

    private void insertEvaluation(TaskActionCommand command, ProjectTaskInstanceDO task,
                                  ProjectTaskExecutionContractDO contract, TaskWorkbenchActor actor,
                                  LocalDateTime occurredAt, CompletionDecision decision) {
        ProjectTaskCompletionEvaluationDO evaluation = new ProjectTaskCompletionEvaluationDO();
        evaluation.setId(decision.evaluationId());
        evaluation.setTenantId(actor.tenantId());
        evaluation.setProjectTaskId(task.getId());
        evaluation.setExecutionContractId(contract.getId());
        evaluation.setTaskVersion(task.getVersion());
        evaluation.setContractVersion(contract.getContractVersion());
        evaluation.setEvaluationResultCode(decision.satisfied() ? "SATISFIED" : "NOT_SATISFIED");
        evaluation.setUnmetItemsJson(JsonUtils.toJsonString(decision.unmetItems()));
        evaluation.setCommandId(command.idempotencyKey());
        evaluation.setIdempotencyKey(command.idempotencyKey());
        evaluation.setFactContextCode("PROJ");
        evaluation.setFactObjectType("ProjectTask");
        evaluation.setFactObjectKey(String.valueOf(task.getId()));
        evaluation.setFactVersion((long) task.getVersion());
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
        detail.put("executionContractId", facts.contractId());
        detail.put("contractVersion", facts.contractVersion());
        if (facts.evaluationId() != null) {
            detail.put("completionEvaluationId", facts.evaluationId());
            detail.put("completionResult", facts.completionSatisfied() ? "SATISFIED" : "NOT_SATISFIED");
            detail.put("unmetItems", facts.unmetItems());
        }
        String eventType = "COMPLETE".equals(action) && facts.completionSatisfied() ? "TaskCompleted" : null;
        String eventPayload = eventType == null ? null : JsonUtils.toJsonString(new TaskCompletedMessage.Payload(
                actor.tenantId(), facts.projectId(), result.taskId(), facts.evaluationId(), result.taskVersion(),
                facts.contractId(), facts.contractVersion(), (long) facts.beforeTaskVersion(), actor.actorId(),
                facts.occurredAt()));
        return new PlatformCommandExecutionApi.SuccessFacts("PROJECT_TASK_" + action, "ProjectTask",
                String.valueOf(result.taskId()), actor.correlationId(), JsonUtils.toJsonString(detail),
                eventType, eventPayload);
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
        detail.put("failureCode", ex instanceof ServiceException service ? String.valueOf(service.getCode())
                : ex.getMessage() == null ? "PROJECT_TASK_ACTION_FAILED" : ex.getMessage());
        operationAuditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(),
                "PROJECT_TASK_ACTION", "ProjectTask",
                command == null || command.taskId() == null ? "UNKNOWN" : String.valueOf(command.taskId()),
                "REJECTED", Collections.unmodifiableMap(detail));
    }

    private record CompletionDecision(boolean satisfied, List<String> unmetItems, Long evaluationId,
                                      String gateSnapshot, LocalDateTime occurredAt) {
        static CompletionDecision notApplicable() {
            return new CompletionDecision(true, List.of(), null, null, null);
        }
    }

    private record ActionFacts(Long projectId, String beforeStatus, int beforeTaskVersion,
                               Long stateMachineRevisionId, Long contractId, Integer contractVersion,
                               Long evaluationId, boolean completionSatisfied, List<String> unmetItems,
                               LocalDateTime occurredAt) {
        static ActionFacts changed(ProjectTaskInstanceDO task, ProjectTaskExecutionContractDO contract,
                                   CompletionDecision completion, LocalDateTime occurredAt) {
            return create(task, contract, completion, occurredAt);
        }
        static ActionFacts evaluated(ProjectTaskInstanceDO task, ProjectTaskExecutionContractDO contract,
                                     CompletionDecision completion, LocalDateTime occurredAt) {
            return create(task, contract, completion, occurredAt);
        }
        private static ActionFacts create(ProjectTaskInstanceDO task, ProjectTaskExecutionContractDO contract,
                                          CompletionDecision completion, LocalDateTime occurredAt) {
            return new ActionFacts(task.getProjectId(), task.getStatus(), task.getVersion(),
                    task.getStateMachineRevisionId(), contract.getId(), contract.getContractVersion(),
                    completion.evaluationId(), completion.satisfied(), completion.unmetItems(), occurredAt);
        }
    }
}
