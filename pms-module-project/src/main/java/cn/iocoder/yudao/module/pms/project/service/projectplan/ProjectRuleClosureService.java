package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcessQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectRuleClosureService {
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectGateReferenceInstanceMapper references;
    private final ProjectRuntimeRuleEvaluator rules;
    private final OperationAuditApi audit;
    private final ProjectStageGateProcessOwnerApi processes;
    public record Closure(boolean closed, boolean unknown) { }

    @Transactional(rollbackFor = Exception.class)
    public Closure closeIfSatisfied(Long projectId, Long actorId, String correlationId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenantId, projectId));
        if (project == null || !"ACTIVE".equals(project.getLifecycleStatus())) return new Closure(false, false);
        var scope = new ProjectPlanScopeQuery(tenantId, projectId);
        var plan = plans.selectEffective(scope);
        if (plan == null) return new Closure(false, true);
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        String key = snapshot.getClosureRuleKey();
        if (key == null || key.isBlank()) return new Closure(false, false); // No implicit terminal-stage closure or approval.
        var query = new ProjectRuntimeGraphQuery(tenantId, projectId);
        var stages = graph.selectStagesForUpdate(query);
        if (stages.stream().anyMatch(stage -> !Set.of("PENDING", "DONE", "TERMINATED").contains(stage.getStatus())))
            return new Closure(false, false);
        var tasks = graph.selectTasksForUpdate(query);
        if (tasks.stream().anyMatch(task -> (task.getActualStartTime() != null || Set.of("IN_PROGRESS", "PENDING_ACCEPT").contains(task.getStatus()))
                && !Set.of("DONE", "CLOSED").contains(task.getStatus()))) return new Closure(false, false);
        if (executions.selectCurrentForUpdate(scope).stream().anyMatch(round -> round.getStartedAt() != null
                && !Set.of("DONE", "TERMINATED").contains(round.getStatus()))) return new Closure(false, false);
        var program = snapshot.getRulePrograms().get(key);
        if (program == null) return new Closure(false, true);
        var gates = graph.selectGatesForUpdate(query);
        var refs = gates.isEmpty() ? List.<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO>of()
                : references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(tenantId, gates.stream().map(g -> g.getId()).toList()));
        if (refs.stream().anyMatch(ref -> "PROCESS".equals(ref.getRefType()) || "APPROVAL".equals(ref.getRefType()))) {
            try {
                var running = processes.inspectRunning(new ProjectStageGateRunningProcessQuery(tenantId, projectId));
                if (running == null) return new Closure(false, true);
                if (!running.isEmpty()) return new Closure(false, false);
            } catch (RuntimeException unavailable) { return new Closure(false, true); }
        }
        var result = rules.evaluate("plan:" + plan.getId() + ":closure:" + key, program,
                new ProjectRuntimeRuleEvaluator.Facts(project, null, tasks, gates, refs, false));
        if (!result.matched()) return new Closure(false, result.outcome() == RuleEvaluation.Outcome.UNKNOWN);
        var now = LocalDateTime.now();
        var update = new ProjectPlanVersionMapper.RuleClosure(tenantId, projectId, plan.getId(), project.getVersion(), now,
                JsonUtils.toJsonString(result), actorId == null ? "project-rules" : actorId.toString());
        if (plans.closeProjectIfActive(update) != 1 || plans.recordClosureIfOpen(update) != 1)
            throw new IllegalStateException("PROJECT_RULE_CLOSURE_VERSION_CONFLICT");
        audit.record(tenantId, actorId, correlationId, "PROJECT_CLOSED_BY_RULE", "Project", projectId.toString(), "SUCCESS",
                Map.of("planVersionId", plan.getId(), "ruleKey", key, "closedAt", now));
        return new Closure(true, false);
    }
}
