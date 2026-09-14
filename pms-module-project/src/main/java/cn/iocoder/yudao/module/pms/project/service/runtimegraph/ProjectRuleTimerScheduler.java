package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.AbsoluteTimeCondition;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Registers in the same creation/apply/rework transaction. Existing Quartz Outbox delivery owns wakeup and retry. */
@Component
@RequiredArgsConstructor
public class ProjectRuleTimerScheduler {
    private final ProjectNodeExecutionMapper executions;
    private final PlatformBusinessEventApi events;

    @Transactional(propagation = Propagation.MANDATORY)
    public void schedule(Long projectId, Long planId, TemplateExecutionSnapshot snapshot, Set<Long> newRoundIds) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var rounds = executions.selectCurrent(new ProjectPlanScopeQuery(tenantId, projectId));
        for (var round : rounds) {
            if (!Objects.equals(round.getPlanVersionId(), planId) || !Set.of("PENDING", "ACTIVE").contains(round.getStatus())
                    || (newRoundIds != null && !newRoundIds.contains(round.getId()))) continue;
            if ("STAGE".equals(round.getNodeKind())) {
                var node = snapshot.getStages().stream().filter(item -> round.getNodeKey().equals(item.getNodeKey())).findFirst().orElseThrow();
                nodeTimers(tenantId, projectId, planId, snapshot, round, node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey());
            } else if ("TASK".equals(round.getNodeKind())) {
                var node = snapshot.getTasks().stream().filter(item -> round.getNodeKey().equals(item.getNodeKey())).findFirst().orElseThrow();
                nodeTimers(tenantId, projectId, planId, snapshot, round, node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey());
            }
        }
        append(tenantId, projectId, planId, snapshot, null, rounds.stream().map(ProjectNodeExecutionDO::getId).sorted().toList(),
                ProjectRuleTimer.Purpose.CLOSURE, snapshot.getClosureRuleKey());
    }

    private void nodeTimers(Long tenant, Long project, Long plan, TemplateExecutionSnapshot snapshot, ProjectNodeExecutionDO round,
                            String admission, String completion, String exit) {
        if ("PENDING".equals(round.getStatus())) append(tenant, project, plan, snapshot, round.getId(), List.of(), ProjectRuleTimer.Purpose.ADMISSION, admission);
        append(tenant, project, plan, snapshot, round.getId(), List.of(), ProjectRuleTimer.Purpose.COMPLETION, completion);
        append(tenant, project, plan, snapshot, round.getId(), List.of(), ProjectRuleTimer.Purpose.EXIT, exit);
    }

    private void append(Long tenant, Long project, Long plan, TemplateExecutionSnapshot snapshot, Long execution,
                         List<Long> closureExecutions, ProjectRuleTimer.Purpose purpose, String key) {
        if (key == null || key.isBlank()) return;
        var program = snapshot.getRulePrograms().get(key);
        if (program == null) throw new IllegalArgumentException("TIMER_RULE_NOT_FROZEN");
        // All time boundaries matter even inside NOT/OR; LiteFlow determines the actual result at delivery.
        var deadlines = program.leaves().stream().filter(leaf -> AbsoluteTimeCondition.PREDICATE.equals(leaf.predicate()))
                .map(leaf -> AbsoluteTimeCondition.deadline(leaf.parameters())).distinct().toList();
        for (var deadline : deadlines) {
            var timer = ProjectRuleTimer.create(tenant, project, plan, execution, closureExecutions, purpose, key, deadline);
            events.appendAt("ProjectPlan", plan.toString(), timer.event(), LocalDateTime.ofInstant(deadline, ZoneId.systemDefault()));
        }
    }
}
