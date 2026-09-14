package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectApprovalProcessCreationApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/** Reuses BPM creation, permission and approval handling; only project execution identity is adapted here. */
@Service
@RequiredArgsConstructor
public class PmsNodeApprovalProcessOwner implements ProjectNodeApprovalApi {
    private final ProjectApprovalProcessCreationApi creation;
    private final HistoryService history;
    private final ProjectNodeExecutionApi executions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @DataPermission(enable = false) // Same user lookup semantics as the original BPM creation service.
    public Fact start(Start command) {
        if (command == null || command.actorId() == null || command.actorId() <= 0 || command.execution() == null
                || command.operationId() == null || command.operationId().isBlank())
            throw new IllegalArgumentException("TASK_APPROVAL_START_INVALID");
        var scope = command.scope();
        validate(scope);
        if (scope.kind() != NodeKind.TASK) throw new IllegalArgumentException("TASK_APPROVAL_SCOPE_INVALID");
        var current = executions.lockAndRevalidate(command.execution());
        if (!Objects.equals(scope.projectId(), current.projectId()) || !Objects.equals(scope.nodeId(), current.taskId())
                || !Objects.equals(scope.executionId(), current.executionId())
                || !Objects.equals(scope.contractId(), current.executionContractId())
                || !Objects.equals(scope.startedAt(), current.startedAt()))
            throw new IllegalArgumentException("TASK_APPROVAL_EXECUTION_MISMATCH");
        return startRound(scope, command.actorId(), command.operationId(), command.variables(), command.selectedApprovers());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    @DataPermission(enable = false)
    public Fact startStage(StageStart command) {
        if (command == null || !positive(command.actorId()) || command.execution() == null
                || command.operationId() == null || command.operationId().isBlank())
            throw new IllegalArgumentException("STAGE_APPROVAL_START_INVALID");
        var scope = command.scope();
        validate(scope);
        if (scope.kind() != NodeKind.STAGE) throw new IllegalArgumentException("STAGE_APPROVAL_SCOPE_INVALID");
        var current = executions.lockAndRevalidateStage(command.execution());
        if (!Objects.equals(scope.projectId(), current.projectId()) || !Objects.equals(scope.nodeId(), current.stageId())
                || !Objects.equals(scope.executionId(), current.executionId())
                || !Objects.equals(scope.contractId(), current.executionContractId()))
            throw new IllegalArgumentException("STAGE_APPROVAL_EXECUTION_MISMATCH");
        return startRound(scope, command.actorId(), command.operationId(), command.variables(), command.selectedApprovers());
    }

    private Fact startRound(Scope scope, Long actorId, String operationId, Map<String, Object> form,
                            Map<String, List<Long>> selectedApprovers) {
        // Project-first lock serializes creation for this round, including requests with different retry keys.
        var existing = instances(scope);
        if (!existing.isEmpty()) {
            var fact = evaluate(scope, existing);
            if (fact.outcome() == Outcome.UNKNOWN) throw new IllegalStateException(fact.reason());
            var replay = existing.stream().filter(instance -> Objects.equals(operationId,
                    instance.getProcessVariables().get(scope.kind().variable("ApprovalOperation"))) && Objects.equals(actorId,
                    instance.getProcessVariables().get(scope.kind().variable("ActorId")))).findFirst().orElse(null);
            if (replay != null) return instanceFact(scope, replay);
            if (!List.of("REJECTED", "CANCELLED").contains(fact.status()))
                throw new IllegalStateException(scope.kind() + "_APPROVAL_ATTEMPT_STILL_EFFECTIVE");
        }
        var variables = new HashMap<String, Object>();
        if (form != null) variables.putAll(form);
        if (variables.keySet().stream().anyMatch(scope.kind()::reserved))
            throw new IllegalArgumentException(scope.kind() + "_APPROVAL_RESERVED_VARIABLE");
        variables.putAll(scope.identity());
        variables.put(scope.kind().variable("ActorId"), actorId);
        variables.put(scope.kind().variable("ApprovalOperation"), operationId);
        variables.put(scope.kind().variable("ApprovalAttempt"), existing.stream().mapToInt(instance -> attempt(scope, instance)).max().orElse(0) + 1);
        String id = creation.create(new ProjectApprovalProcessCreationApi.Command(scope.tenantId(), actorId,
                scope.definitionKey(), scope.definitionId(), scope.businessKey(), variables, selectedApprovers));
        var result = evaluate(scope, instances(scope));
        if (!Objects.equals(id, result.processInstanceId())) throw new IllegalStateException(scope.kind() + "_APPROVAL_START_UNCONFIRMED");
        return result;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Fact inspect(Scope scope) {
        validate(scope);
        return evaluate(scope, instances(scope));
    }

    private List<HistoricProcessInstance> instances(Scope scope) {
        return history.createHistoricProcessInstanceQuery().processInstanceBusinessKey(scope.businessKey())
                .processInstanceTenantId(FlowableUtils.getTenantId()).includeProcessVariables().list();
    }

    private Fact evaluate(Scope scope, List<HistoricProcessInstance> instances) {
        if (instances.isEmpty()) return new Fact(Outcome.NOT_SATISFIED, "NOT_STARTED", null, scope.definitionId(), "APPROVAL_NOT_STARTED");
        var numbers = new HashSet<Integer>();
        var operations = new HashSet<List<Object>>();
        for (var instance : instances) {
            var fact = instanceFact(scope, instance);
            if (fact.outcome() == Outcome.UNKNOWN) return fact;
            var variables = instance.getProcessVariables();
            if (!(variables.get(scope.kind().variable("ApprovalAttempt")) instanceof Integer number) || number <= 0 || !numbers.add(number)
                    || !(variables.get(scope.kind().variable("ApprovalOperation")) instanceof String operation) || operation.isBlank()
                    || !(variables.get(scope.kind().variable("ActorId")) instanceof Long actor) || actor <= 0
                    || !operations.add(List.of(actor, operation)))
                return Fact.unknown(scope.kind() + "_APPROVAL_ATTEMPT_IDENTITY_INVALID");
        }
        if (instances.stream().filter(instance -> instance.getEndTime() == null).count() > 1)
            return Fact.unknown(scope.kind() + "_APPROVAL_MULTIPLE_ACTIVE_INSTANCES");
        return instanceFact(scope, instances.stream().max(Comparator.comparingInt(instance -> attempt(scope, instance))).orElseThrow());
    }

    private int attempt(Scope scope, HistoricProcessInstance instance) {
        return (Integer) instance.getProcessVariables().get(scope.kind().variable("ApprovalAttempt"));
    }

    private Fact instanceFact(Scope scope, HistoricProcessInstance instance) {
        var variables = instance.getProcessVariables();
        if (!Objects.equals(scope.definitionId(), instance.getProcessDefinitionId())
                || !Objects.equals(scope.definitionKey(), instance.getProcessDefinitionKey()) || variables == null
                || scope.identity().entrySet().stream().anyMatch(entry -> !Objects.equals(entry.getValue(), variables.get(entry.getKey()))))
            return Fact.unknown(scope.kind() + "_APPROVAL_IDENTITY_MISMATCH");
        if (instance.getStartTime() == null || instance.getStartTime().toInstant()
                .isBefore(scope.startedAt().atZone(ZoneId.systemDefault()).toInstant()))
            return Fact.unknown(scope.kind() + "_APPROVAL_ROUND_EVIDENCE_UNAVAILABLE");
        Object status = variables.get("PROCESS_STATUS");
        if (!(status instanceof Integer value) || value < 1 || value > 4)
            return Fact.unknown(scope.kind() + "_APPROVAL_STATUS_UNKNOWN");
        boolean ended = instance.getEndTime() != null;
        if (ended && value == 1) return Fact.unknown(scope.kind() + "_APPROVAL_RESULT_UNAVAILABLE");
        String state = switch (value) { case 2 -> "APPROVED"; case 3 -> "REJECTED"; case 4 -> "CANCELLED"; default -> "RUNNING"; };
        return new Fact(value == 2 && ended ? Outcome.SATISFIED : Outcome.NOT_SATISFIED,
                ended ? state : "RUNNING", instance.getId(), instance.getProcessDefinitionId(),
                value == 2 && ended ? null : "APPROVAL_" + (ended ? state : "RUNNING"));
    }

    private void validate(Scope scope) {
        if (scope == null || scope.kind() == null || !Objects.equals(TenantContextHolder.getRequiredTenantId(), scope.tenantId())
                || !positive(scope.projectId()) || !positive(scope.nodeId()) || !positive(scope.executionId())
                || !positive(scope.contractId()) || scope.startedAt() == null
                || scope.definitionKey() == null || scope.definitionKey().isBlank()
                || scope.definitionId() == null || scope.definitionId().isBlank())
            throw new IllegalArgumentException("NODE_APPROVAL_SCOPE_INVALID");
    }
    private boolean positive(Long value) { return value != null && value > 0; }
}
