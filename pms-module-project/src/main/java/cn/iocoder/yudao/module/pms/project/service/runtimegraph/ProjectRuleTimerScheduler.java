package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.AbsoluteTimeCondition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RelativeTimeCondition;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
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
    private final ProjectPlanVersionMapper plans;

    @Transactional(propagation = Propagation.MANDATORY)
    public void schedule(Long projectId, Long planId, TemplateExecutionSnapshot snapshot, Set<Long> newRoundIds) {
        TemplateExecutionSnapshotReader.validate(snapshot);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var rounds = executions.selectCurrent(new ProjectPlanScopeQuery(tenantId, projectId));
        schedule(tenantId, projectId, planId, snapshot, rounds, newRoundIds, null);
    }

    /** Called only after a real activation/completion transition, in its existing transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleFromNode(Long projectId, String kind, Long nodeInstanceId) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        var scope = new ProjectPlanScopeQuery(tenant, projectId);
        var plan = plans.selectEffective(scope);
        if (plan == null) throw new IllegalStateException("TIMER_PLAN_UNAVAILABLE");
        var rounds = executions.selectCurrent(scope);
        var changed = rounds.stream().filter(round -> kind.equals(round.getNodeKind())
                && nodeInstanceId.equals(round.getNodeInstanceId())).toList();
        if (changed.size() != 1) throw new IllegalStateException("TIMER_ANCHOR_UNAVAILABLE");
        schedule(tenant, projectId, plan.getId(), TemplateExecutionSnapshotReader.read(plan.getExecutionSnapshot()),
                rounds, null, changed.getFirst().getId());
    }

    private void schedule(Long tenantId, Long projectId, Long planId, TemplateExecutionSnapshot snapshot,
                          List<ProjectNodeExecutionDO> rounds, Set<Long> newRoundIds, Long changedAnchor) {
        for (var round : rounds) {
            if (!Objects.equals(round.getPlanVersionId(), planId) || !Set.of("PENDING", "ACTIVE").contains(round.getStatus())
                    || (newRoundIds != null && !newRoundIds.contains(round.getId()))) continue;
            if ("STAGE".equals(round.getNodeKind())) {
                var node = snapshot.getStages().stream().filter(item -> round.getNodeKey().equals(item.getNodeKey())).findFirst().orElseThrow();
                nodeTimers(tenantId, projectId, planId, snapshot, round, node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey(), rounds, changedAnchor);
            } else if ("TASK".equals(round.getNodeKind())) {
                var node = snapshot.getTasks().stream().filter(item -> round.getNodeKey().equals(item.getNodeKey())).findFirst().orElseThrow();
                nodeTimers(tenantId, projectId, planId, snapshot, round, node.getAdmissionRuleKey(), node.getCompletionRuleKey(), node.getExitRuleKey(), rounds, changedAnchor);
            }
        }
        append(tenantId, projectId, planId, snapshot, null, rounds.stream().map(ProjectNodeExecutionDO::getId).sorted().toList(),
                ProjectRuleTimer.Purpose.CLOSURE, snapshot.getClosureRuleKey(), rounds, changedAnchor);
    }

    private void nodeTimers(Long tenant, Long project, Long plan, TemplateExecutionSnapshot snapshot, ProjectNodeExecutionDO round,
                            String admission, String completion, String exit, List<ProjectNodeExecutionDO> rounds, Long changedAnchor) {
        if ("PENDING".equals(round.getStatus())) append(tenant, project, plan, snapshot, round.getId(), List.of(), ProjectRuleTimer.Purpose.ADMISSION, admission, rounds, changedAnchor);
        append(tenant, project, plan, snapshot, round.getId(), List.of(), ProjectRuleTimer.Purpose.COMPLETION, completion, rounds, changedAnchor);
        append(tenant, project, plan, snapshot, round.getId(), List.of(), ProjectRuleTimer.Purpose.EXIT, exit, rounds, changedAnchor);
    }

    private void append(Long tenant, Long project, Long plan, TemplateExecutionSnapshot snapshot, Long execution,
                         List<Long> closureExecutions, ProjectRuleTimer.Purpose purpose, String key,
                         List<ProjectNodeExecutionDO> rounds, Long changedAnchor) {
        if (key == null || key.isBlank()) return;
        var program = snapshot.getRulePrograms().get(key);
        if (program == null) throw new IllegalArgumentException("TIMER_RULE_NOT_FROZEN");
        // All time boundaries matter even inside NOT/OR; LiteFlow determines the actual result at delivery.
        var deadlines = new java.util.LinkedHashSet<ProjectRelativeTimeFacts.Boundary>();
        for (var leaf : program.leaves()) {
            if (AbsoluteTimeCondition.PREDICATE.equals(leaf.predicate()) && changedAnchor == null)
                deadlines.add(new ProjectRelativeTimeFacts.Boundary(null, AbsoluteTimeCondition.deadline(leaf.parameters())));
            if (RelativeTimeCondition.PREDICATE.equals(leaf.predicate())) {
                var boundary = ProjectRelativeTimeFacts.boundary(leaf.parameters(), execution, rounds);
                if (boundary != null && (changedAnchor == null || changedAnchor.equals(boundary.executionId()))) deadlines.add(boundary);
            }
        }
        for (var boundary : deadlines) {
            var timer = ProjectRuleTimer.create(tenant, project, plan, execution, closureExecutions, purpose, key, boundary.dueAt(), boundary.executionId());
            events.appendAt("ProjectPlan", plan.toString(), timer.event(), LocalDateTime.ofInstant(boundary.dueAt(), ZoneId.systemDefault()));
        }
    }
}
