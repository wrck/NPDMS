package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAssignmentCommandQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared automatic admission command. Admission is not START, assignment or a business submission. */
@Service
@RequiredArgsConstructor
public class ProjectTaskAdmissionService {
    @jakarta.annotation.Resource
    private ProjectRuleTimerScheduler timers;
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectTaskExecutionContractMapper contracts;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectStageAdmissionService rules;
    private final OperationAuditApi audit;

    public record Result(boolean activated, boolean unknown) { }

    // REQUIRED joins a guarded timer transaction, or opens one per task in ordinary reevaluation.
    // https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
    @Transactional(rollbackFor = Exception.class)
    public Result activateEligible(Long projectId, Long taskId, String correlationId) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenant, projectId));
        if (project == null || !tenant.equals(project.getTenantId())) return new Result(false, true);
        if (!"ACTIVE".equals(project.getLifecycleStatus())) return new Result(false, false);
        if (project.getActivePlanVersionId() == null) return new Result(false, true);
        var task = projects.selectTaskForAssignmentForUpdate(new TaskAssignmentCommandQuery(tenant, projectId, taskId));
        var rounds = executions.selectCurrentForUpdate(new ProjectPlanScopeQuery(tenant, projectId)).stream()
                .filter(round -> "TASK".equals(round.getNodeKind()) && taskId.equals(round.getNodeInstanceId())).toList();
        if (task == null || !tenant.equals(task.getTenantId()) || !projectId.equals(task.getProjectId())
                || rounds.size() != 1) return new Result(false, true);
        var round = rounds.getFirst();
        // Completed history can intentionally belong to an older plan. Never admit or rewrite it.
        if (Set.of("ACTIVE", "DONE", "TERMINATED").contains(round.getStatus())) return new Result(false, false);
        if (!"PENDING".equals(round.getStatus()) || !project.getActivePlanVersionId().equals(round.getPlanVersionId()))
            return new Result(false, true);
        var contract = contracts.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(tenant, taskId));
        if (contract == null || !tenant.equals(contract.getTenantId()) || !taskId.equals(contract.getProjectTaskId())
                || !Objects.equals(contract.getId(), round.getContractId())
                || !Objects.equals(contract.getSourceNodeKey(), round.getNodeKey())) return new Result(false, true);
        var fact = rules.taskAdmissionFact(project, task, contract);
        if (!fact.available()) return new Result(false, true);
        if (!Boolean.TRUE.equals(fact.value())) return new Result(false, false);
        if (executions.activateIfPending(new ProjectNodeExecutionMapper.Activation(tenant, projectId,
                round.getPlanVersionId(), taskId, "TASK", LocalDateTime.now())) != 1)
            throw new IllegalStateException("TASK_ADMISSION_ROUND_CONFLICT");
        timers.scheduleFromNode(projectId, "TASK", taskId);
        audit.record(tenant, null, correlationId, "PROJECT_TASK_ADMITTED", "ProjectTask", taskId.toString(), "SUCCESS",
                Map.of("planVersionId", round.getPlanVersionId(), "executionId", round.getId(),
                        "contractId", contract.getId(), "nodeKey", round.getNodeKey(),
                        "ruleVersionRef", "plan:" + round.getPlanVersionId() + ":task:" + taskId + ":admission"));
        return new Result(true, false);
    }
}
