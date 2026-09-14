package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.*;
import cn.iocoder.yudao.module.pms.project.domain.rule.AbsoluteTimeCondition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectRuleClosureService;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectStageCompletionService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One timer, one guarded node transaction. No outer project transaction or secondary retry loop. */
@Service
@RequiredArgsConstructor
public class ProjectRuleTimerDelivery {
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectTaskExecutionContractMapper contracts;
    private final ProjectStageAdmissionService admission;
    private final ProjectStageCompletionService stages;
    private final ProjectTaskLifecycleService tasks;
    private final ProjectRuleClosureService closure;
    private final PlatformBusinessEventApi events;
    private final OperationAuditApi audit;

    /** True acknowledges applied, unsatisfied or obsolete timers; false asks the existing Outbox to retry unknown. */
    @Transactional(rollbackFor = Exception.class)
    public boolean deliver(ProjectRuleTimer timer) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        if (!tenant.equals(timer.tenantId()) || timer.projectId() == null || timer.planVersionId() == null
                || timer.purpose() == null || timer.dueAt() == null || timer.ruleKey() == null)
            throw new IllegalArgumentException("TIMER_IDENTITY_INVALID");
        if (Instant.now().isBefore(timer.dueAt())) return false;
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenant, timer.projectId()));
        if (project == null || !tenant.equals(project.getTenantId()) || !"ACTIVE".equals(project.getLifecycleStatus())
                || !timer.planVersionId().equals(project.getActivePlanVersionId())) return true;
        var scope = new ProjectPlanScopeQuery(tenant, timer.projectId());
        var plan = plans.selectEffective(scope);
        if (plan == null || !timer.planVersionId().equals(plan.getId())) return false;
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        var rounds = executions.selectCurrentForUpdate(scope);
        boolean wakeDependents;
        if (timer.purpose() == ProjectRuleTimer.Purpose.CLOSURE) {
            if (timer.executionId() != null || !rounds.stream().map(ProjectNodeExecutionDO::getId).sorted().toList().equals(timer.closureExecutionIds())) return true;
            requireRule(timer, snapshot, snapshot.getClosureRuleKey());
            var result = closure.closeIfSatisfied(timer.projectId(), null, timer.eventId());
            return !result.unknown();
        }
        var matches = rounds.stream().filter(round -> Objects.equals(timer.executionId(), round.getId())).toList();
        if (matches.size() != 1) return true;
        var round = matches.getFirst();
        if (!timer.planVersionId().equals(round.getPlanVersionId()) || !Set.of("PENDING", "ACTIVE").contains(round.getStatus())) return true;
        String key;
        if ("STAGE".equals(round.getNodeKind())) {
            var node = snapshot.getStages().stream().filter(item -> round.getNodeKey().equals(item.getNodeKey())).findFirst().orElseThrow();
            key = slot(timer.purpose(), node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey());
            requireRule(timer, snapshot, key);
            if (timer.purpose() == ProjectRuleTimer.Purpose.ADMISSION) {
                var result = admission.activateStage(timer.projectId(), null, timer.eventId(), round.getNodeInstanceId());
                if (result.stream().anyMatch(item -> item.outcome() == RuleEvaluation.Outcome.UNKNOWN)) return false;
                wakeDependents = result.stream().anyMatch(ProjectStageAdmissionService.StageAdmission::activated);
            } else {
                var result = stages.completeStage(timer.projectId(), round.getNodeInstanceId(), null, timer.eventId());
                if (result.unknown()) return false;
                wakeDependents = result.completed() > 0;
            }
        } else if ("TASK".equals(round.getNodeKind())) {
            var node = snapshot.getTasks().stream().filter(item -> round.getNodeKey().equals(item.getNodeKey())).findFirst().orElseThrow();
            key = slot(timer.purpose(), node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey());
            requireRule(timer, snapshot, key);
            if (timer.purpose() == ProjectRuleTimer.Purpose.ADMISSION) {
                if (!"PENDING".equals(round.getStatus())) return true;
                var task = projects.selectTaskForAssignmentForUpdate(new TaskAssignmentCommandQuery(tenant, timer.projectId(), round.getNodeInstanceId()));
                var contract = contracts.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(tenant, round.getNodeInstanceId()));
                if (task == null || contract == null || !Objects.equals(contract.getId(), round.getContractId())) return false;
                var admitted = admission.taskAdmissionFact(project, task, contract);
                if (!admitted.available()) return false;
                if (!Boolean.TRUE.equals(admitted.value())) return true;
                if (executions.activateIfPending(new ProjectNodeExecutionMapper.Activation(tenant, timer.projectId(), timer.planVersionId(),
                        round.getNodeInstanceId(), "TASK", LocalDateTime.now())) != 1) throw new IllegalStateException("TASK_ADMISSION_ROUND_CONFLICT");
                audit.record(tenant, null, timer.eventId(), "PROJECT_TASK_ADMITTED", "ProjectTask", task.getId().toString(), "SUCCESS",
                        Map.of("planVersionId", timer.planVersionId(), "executionId", round.getId(), "ruleKey", key));
                wakeDependents = true;
            } else {
                var result = tasks.completeFromBusinessResult(timer.projectId(), round.getNodeInstanceId(), timer.eventId());
                if (result.unknown()) return false;
                wakeDependents = false; // The task completion command already appends its dedicated reevaluation event.
            }
        } else throw new IllegalArgumentException("TIMER_NODE_KIND_INVALID");
        if (wakeDependents) events.append("Project", timer.projectId().toString(),
                new ProjectRuleReevaluation(tenant, timer.projectId(), null, timer.eventId()).event());
        return true;
    }

    private static String slot(ProjectRuleTimer.Purpose purpose, String admission, String completion, String exit) {
        return switch (purpose) {
            case ADMISSION -> admission;
            case COMPLETION -> completion;
            case EXIT -> exit;
            default -> throw new IllegalArgumentException("TIMER_PURPOSE_INVALID");
        };
    }

    private static void requireRule(ProjectRuleTimer timer, TemplateExecutionSnapshot snapshot, String expectedKey) {
        var program = snapshot.getRulePrograms().get(timer.ruleKey());
        if (!Objects.equals(expectedKey, timer.ruleKey()) || program == null || program.leaves().stream().noneMatch(leaf ->
                AbsoluteTimeCondition.PREDICATE.equals(leaf.predicate()) && AbsoluteTimeCondition.deadline(leaf.parameters()).equals(timer.dueAt())))
            throw new IllegalArgumentException("TIMER_RULE_SCOPE_INVALID");
    }
}
