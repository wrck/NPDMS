package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.StageCompletionEvidence;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;

/** Read-only projection of exact frozen plan versions and immutable round evidence, never latest template data. */
@Service
@RequiredArgsConstructor
public class ProjectExecutionHistoryService {
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;

    public record History(List<Plan> plans, List<Round> rounds) { }
    public record Plan(Long id, Integer revisionNo, String status, LocalDateTime effectiveAt,
                       LocalDateTime closedAt, RuleEvaluation closure) { }
    public record Evaluation(String purpose, String name, RuleEvaluation result) { }
    public record Round(Long id, Long planVersionId, Integer planRevisionNo, String nodeKey, String nodeKind,
                        String nodeCode, String name, Integer roundNo, boolean current, String status,
                        LocalDateTime admittedAt, LocalDateTime startedAt, Long startedPlanVersionId,
                        LocalDateTime submittedAt, Long submittedBy, String submissionNote,
                        boolean canViewSubmissionNote, LocalDateTime endedAt, List<Evaluation> evaluations) { }

    public History get(Long projectId, Long actorId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorId, projectId, ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        boolean canViewNote = permissions.hasAnyPermissions(actorId, "pms:project-task:execute");
        if (canViewNote) {
            var handlingScope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorId, projectId, ProjectScopeApi.ACTION_EDIT));
            canViewNote = handlingScope != null && handlingScope.fullProjectIds() != null && handlingScope.fullProjectIds().contains(projectId);
        }
        final boolean showSubmissionNote = canViewNote;
        var query = new ProjectPlanScopeQuery(tenantId, projectId);
        var versions = plans.selectHistory(query);
        Map<Long, TemplateExecutionSnapshot> snapshots = new HashMap<>();
        Map<Long, Integer> revisions = new HashMap<>();
        var history = versions.stream().map(plan -> {
            snapshots.put(plan.getId(), JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class));
            revisions.put(plan.getId(), plan.getRevisionNo());
            return new Plan(plan.getId(), plan.getRevisionNo(), plan.getStatus(), plan.getEffectiveAt(), plan.getClosedAt(),
                    plan.getClosureResult() == null ? null : JsonUtils.parseObject(plan.getClosureResult(), RuleEvaluation.class));
        }).toList();
        var rounds = executions.selectHistory(query).stream().map(round -> {
            var snapshot = snapshots.get(round.getPlanVersionId());
            String code = round.getNodeKey();
            String name = round.getNodeKey();
            Map<String, String> ruleKeys = new LinkedHashMap<>();
            if (snapshot != null && "STAGE".equals(round.getNodeKind())) {
                var node = snapshot.getStages().stream().filter(item -> round.getNodeKey().equals(item.getNodeKey())).findFirst().orElse(null);
                if (node != null) {
                    code = node.getCode(); name = node.getName();
                    ruleKeys.put("completion", node.getCompletionRuleKey()); ruleKeys.put("exit", node.getExitRuleKey());
                }
            } else if (snapshot != null && "TASK".equals(round.getNodeKind())) {
                var node = snapshot.getTasks().stream().filter(item -> round.getNodeKey().equals(item.getNodeKey())).findFirst().orElse(null);
                if (node != null) {
                    code = node.getCode(); name = node.getName();
                    ruleKeys.put("completion",node.getCompletionRuleKey()); ruleKeys.put("exit",node.getExitRuleKey());
                }
            }
            List<Evaluation> evaluations = new ArrayList<>();
            if (round.getResultSnapshot() != null && snapshot != null) {
                var evidence = JsonUtils.parseObject(round.getResultSnapshot(), StageCompletionEvidence.class);
                ruleKeys.forEach((purpose, key) -> {
                    var result = "completion".equals(purpose) ? evidence.completion() : evidence.exit();
                    if (result == null) return;
                    String ruleName = snapshot.getRules().stream().filter(rule -> Objects.equals(key, rule.key()))
                            .map(rule -> rule.name()).findFirst().orElse(key == null ? "无附加退出条件" : key);
                    evaluations.add(new Evaluation(purpose, ruleName, result));
                });
            }
            return new Round(round.getId(), round.getPlanVersionId(), revisions.get(round.getPlanVersionId()), round.getNodeKey(),
                    round.getNodeKind(), code, name, round.getRoundNo(), Integer.valueOf(1).equals(round.getCurrentMarker()),
                    round.getStatus(), round.getAdmittedAt(), round.getStartedAt(), round.getStartedPlanVersionId(),
                    round.getSubmittedAt(), round.getSubmittedBy(), showSubmissionNote ? round.getSubmissionNote() : null,
                    showSubmissionNote, round.getEndedAt(), List.copyOf(evaluations));
        }).toList();
        return new History(history, rounds);
    }
}
