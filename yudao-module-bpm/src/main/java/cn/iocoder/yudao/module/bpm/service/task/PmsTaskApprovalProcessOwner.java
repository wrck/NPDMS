package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectTaskApprovalApi;
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

/** Reuses BPM creation, permission and approval handling; only project execution identity is adapted here. */
@Service
@RequiredArgsConstructor
public class PmsTaskApprovalProcessOwner implements ProjectTaskApprovalApi {
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
        var current = executions.lockAndRevalidate(command.execution());
        if (!Objects.equals(scope.projectId(), current.projectId()) || !Objects.equals(scope.taskId(), current.taskId())
                || !Objects.equals(scope.executionId(), current.executionId())
                || !Objects.equals(scope.contractId(), current.executionContractId())
                || !Objects.equals(scope.startedAt(), current.startedAt()))
            throw new IllegalArgumentException("TASK_APPROVAL_EXECUTION_MISMATCH");
        // Project-first lock serializes creation for this round, including requests with different retry keys.
        var existing = instances(scope);
        if (!existing.isEmpty()) {
            var fact = evaluate(scope, existing);
            if (fact.outcome() == Outcome.UNKNOWN) throw new IllegalStateException(fact.reason());
            var replay = existing.stream().filter(instance -> Objects.equals(command.operationId(),
                    instance.getProcessVariables().get(VAR_OPERATION)) && Objects.equals(command.actorId(),
                    instance.getProcessVariables().get(VAR_ACTOR))).findFirst().orElse(null);
            if (replay != null) return instanceFact(scope, replay);
            if (!List.of("REJECTED", "CANCELLED").contains(fact.status()))
                throw new IllegalStateException("TASK_APPROVAL_ATTEMPT_STILL_EFFECTIVE");
        }
        var variables = new HashMap<String, Object>();
        if (command.variables() != null) variables.putAll(command.variables());
        if (variables.keySet().stream().anyMatch(key -> key.startsWith("pmsTask")))
            throw new IllegalArgumentException("TASK_APPROVAL_RESERVED_VARIABLE");
        variables.put(VAR_TENANT, scope.tenantId()); variables.put(VAR_PROJECT, scope.projectId());
        variables.put(VAR_TASK, scope.taskId()); variables.put(VAR_EXECUTION, scope.executionId());
        variables.put(VAR_CONTRACT, scope.contractId()); variables.put(VAR_DEFINITION, scope.definitionId());
        variables.put(VAR_ACTOR, command.actorId());
        variables.put(VAR_OPERATION, command.operationId());
        variables.put(VAR_ATTEMPT, existing.stream().mapToInt(this::attempt).max().orElse(0) + 1);
        String id = creation.create(new ProjectApprovalProcessCreationApi.Command(scope.tenantId(), command.actorId(),
                scope.definitionKey(), scope.definitionId(), scope.businessKey(), variables, command.selectedApprovers()));
        var result = evaluate(scope, instances(scope));
        if (!Objects.equals(id, result.processInstanceId())) throw new IllegalStateException("TASK_APPROVAL_START_UNCONFIRMED");
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
            if (!(variables.get(VAR_ATTEMPT) instanceof Integer number) || number <= 0 || !numbers.add(number)
                    || !(variables.get(VAR_OPERATION) instanceof String operation) || operation.isBlank()
                    || !(variables.get(VAR_ACTOR) instanceof Long actor) || actor <= 0
                    || !operations.add(List.of(actor, operation)))
                return Fact.unknown("TASK_APPROVAL_ATTEMPT_IDENTITY_INVALID");
        }
        if (instances.stream().filter(instance -> instance.getEndTime() == null).count() > 1)
            return Fact.unknown("TASK_APPROVAL_MULTIPLE_ACTIVE_INSTANCES");
        return instanceFact(scope, instances.stream().max(Comparator.comparingInt(this::attempt)).orElseThrow());
    }

    private int attempt(HistoricProcessInstance instance) { return (Integer) instance.getProcessVariables().get(VAR_ATTEMPT); }

    private Fact instanceFact(Scope scope, HistoricProcessInstance instance) {
        var variables = instance.getProcessVariables();
        if (!Objects.equals(scope.definitionId(), instance.getProcessDefinitionId())
                || !Objects.equals(scope.definitionKey(), instance.getProcessDefinitionKey()) || variables == null
                || !Objects.equals(scope.tenantId(), variables.get(VAR_TENANT))
                || !Objects.equals(scope.projectId(), variables.get(VAR_PROJECT))
                || !Objects.equals(scope.taskId(), variables.get(VAR_TASK))
                || !Objects.equals(scope.executionId(), variables.get(VAR_EXECUTION))
                || !Objects.equals(scope.contractId(), variables.get(VAR_CONTRACT))
                || !Objects.equals(scope.definitionId(), variables.get(VAR_DEFINITION)))
            return Fact.unknown("TASK_APPROVAL_IDENTITY_MISMATCH");
        if (instance.getStartTime() == null || instance.getStartTime().toInstant()
                .isBefore(scope.startedAt().atZone(ZoneId.systemDefault()).toInstant()))
            return Fact.unknown("TASK_APPROVAL_ROUND_EVIDENCE_UNAVAILABLE");
        Object status = variables.get("PROCESS_STATUS");
        if (!(status instanceof Integer value) || value < 1 || value > 4)
            return Fact.unknown("TASK_APPROVAL_STATUS_UNKNOWN");
        boolean ended = instance.getEndTime() != null;
        if (ended && value == 1) return Fact.unknown("TASK_APPROVAL_RESULT_UNAVAILABLE");
        String state = switch (value) { case 2 -> "APPROVED"; case 3 -> "REJECTED"; case 4 -> "CANCELLED"; default -> "RUNNING"; };
        return new Fact(value == 2 && ended ? Outcome.SATISFIED : Outcome.NOT_SATISFIED,
                ended ? state : "RUNNING", instance.getId(), instance.getProcessDefinitionId(),
                value == 2 && ended ? null : "APPROVAL_" + (ended ? state : "RUNNING"));
    }

    private void validate(Scope scope) {
        if (scope == null || !Objects.equals(TenantContextHolder.getRequiredTenantId(), scope.tenantId())
                || !positive(scope.projectId()) || !positive(scope.taskId()) || !positive(scope.executionId())
                || !positive(scope.contractId()) || scope.startedAt() == null
                || scope.definitionKey() == null || scope.definitionKey().isBlank()
                || scope.definitionId() == null || scope.definitionId().isBlank())
            throw new IllegalArgumentException("TASK_APPROVAL_SCOPE_INVALID");
    }
    private boolean positive(Long value) { return value != null && value > 0; }
}
