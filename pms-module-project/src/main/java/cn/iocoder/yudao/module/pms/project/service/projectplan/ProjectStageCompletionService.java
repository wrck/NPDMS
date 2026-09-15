package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcessQuery;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectGateReferenceForUpdateQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageStatusUpdate;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.query.ProjectPlanScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.query.ProjectRuntimeGraphQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskProjectLockQuery;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.StageCompletionEvidence;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleEvaluationService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionQuery;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessLinkFact;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskBusinessCompletionEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectStageCompletionService {
    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerScheduler timers;
    private final ProjectTaskRuntimeMapper projects;
    private final ProjectPlanVersionMapper plans;
    private final ProjectNodeExecutionMapper executions;
    private final ProjectRuntimeGraphMapper graph;
    private final ProjectStageInstanceMapper stages;
    private final ProjectGateReferenceInstanceMapper references;
    private final ProjectRuleEvaluationService rules;
    private final ProjectRuleCompiler compiler;
    private final ProjectRuntimeRuleEvaluator facts;
    private final OperationAuditApi audit;
    private final ProjectNodeExecutionApi nodeContexts;
    private final ProjectTaskBusinessService business;
    private final ProjectStageGateProcessOwnerApi processes;
    private final cn.iocoder.yudao.module.pms.project.service.stagebusiness.ProjectStageApprovalService approvals;

    public record Completion(int completed, boolean unknown) { }

    @Transactional(rollbackFor = Exception.class)
    public Completion completeStage(Long projectId, Long stageId, Long actorId, String correlationId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        var project = projects.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(tenantId, projectId));
        if (project == null || !"ACTIVE".equals(project.getLifecycleStatus())) return new Completion(0, false);
        var scope = new ProjectPlanScopeQuery(tenantId, projectId);
        var plan = plans.selectEffective(scope);
        if (plan == null || !Objects.equals(plan.getId(),project.getActivePlanVersionId())) return new Completion(0, true);
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        var query = new ProjectRuntimeGraphQuery(tenantId, projectId);
        var stageRows = graph.selectStagesForUpdate(query).stream().filter(stage -> Objects.equals(stageId,stage.getId())).toList();
        if (stageRows.size()!=1) return new Completion(0,true);
        var tasks = graph.selectTasksForUpdate(query);
        var rounds = executions.selectCurrentForUpdate(scope);
        var startedTasks = new java.util.HashSet<Long>();
        rounds.stream().filter(round -> "TASK".equals(round.getNodeKind()) && round.getStartedAt() != null)
                .forEach(round -> startedTasks.add(round.getNodeInstanceId()));
        var gates = graph.selectGatesForUpdate(query);
        var gateRefs = gates.isEmpty() ? List.<cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO>of()
                : references.selectOrderedForUpdate(new ProjectGateReferenceForUpdateQuery(tenantId, gates.stream().map(g -> g.getId()).toList()));
        int completed = 0;
        boolean unknown = false;
        for (var stage : stageRows) {
            if (!"ACTIVE".equals(stage.getStatus())) continue;
            var definitions = snapshot.getStages().stream().filter(node -> stage.getCode().equals(node.getCode())).toList();
            var current = rounds.stream().filter(round -> "STAGE".equals(round.getNodeKind())
                    && stage.getId().equals(round.getNodeInstanceId()) && plan.getId().equals(round.getPlanVersionId())).toList();
            if (definitions.size() != 1 || current.size() != 1 || !"ACTIVE".equals(current.getFirst().getStatus())) { unknown = true; continue; }
            var definition = definitions.getFirst();
            var round = current.getFirst();
            if (!Objects.equals(definition.getNodeKey(), round.getNodeKey())) { unknown = true; continue; }
            // Native submission and Owner completion are distinct forms of real handling evidence.
            var binding = definition.getBinding();
            boolean nativeWork = binding == null || "STAGE_NATIVE".equals(binding.getType());
            if (binding != null && nativeWork && round.getSubmittedAt() == null) continue;
            List<TaskBusinessLinkFact> ownerLinks = List.of();
            cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Fact approval = null;
            if (!nativeWork) {
                var execution = nodeContexts.inspectStage(new ProjectStageExecutionQuery(projectId,stage.getId(),round.getContractId()));
                if (!Objects.equals(execution.executionId(),round.getId()) || !Objects.equals(execution.planVersionId(),plan.getId()))
                    return new Completion(0,true);
                if ("APPROVAL".equals(binding.getType())) {
                    try {
                        approval = approvals.view(tenantId, execution, binding).current();
                        if (approval.outcome() == cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.UNKNOWN) {
                            unknown = true; continue;
                        }
                        if (approval.outcome() != cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.SATISFIED) continue;
                    } catch (RuntimeException unavailable) { unknown = true; continue; }
                } else {
                    var owner = business.lockStageCompletionFacts(tenantId,execution,binding);
                    if (owner == null || owner.facts() == null || owner.facts().links().isEmpty()) return new Completion(0,true);
                    if (!owner.hasCompletedHandling()) continue;
                    ownerLinks = owner.facts().links();
                }
            }
            boolean unfinishedWork = tasks.stream().filter(task -> stage.getCode().equals(task.getStageCode()))
                    .anyMatch(task -> (task.getActualStartTime() != null || startedTasks.contains(task.getId()) || Set.of("IN_PROGRESS", "PENDING_ACCEPT").contains(task.getStatus()))
                            && !Set.of("DONE", "CLOSED").contains(task.getStatus()));
            if (unfinishedWork) continue;
            var processRefs = gateRefs.stream().filter(ref -> "PROCESS".equals(ref.getRefType()) || "APPROVAL".equals(ref.getRefType()))
                    .filter(ref -> gates.stream().anyMatch(gate -> Objects.equals(gate.getId(), ref.getGateId())
                            && Objects.equals(gate.getStageCode(), stage.getCode())))
                    .map(cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectGateReferenceInstanceDO::getId)
                    .collect(java.util.stream.Collectors.toSet());
            if (!processRefs.isEmpty()) {
                try {
                    var running = processes.inspectRunning(new ProjectStageGateRunningProcessQuery(tenantId, projectId));
                    if (running == null) throw new IllegalStateException("BPM_ACTIVITY_UNAVAILABLE");
                    if (running.stream().anyMatch(process -> processRefs.contains(process.gateReferenceId()))) continue;
                } catch (RuntimeException unavailable) { unknown = true; continue; }
            }
            var context = new ProjectRuntimeRuleEvaluator.Facts(project, stage, tasks, gates, gateRefs, true);
            RuleEvaluation completion;
            RuleEvaluation exit;
            try {
                completion = evaluate(snapshot, plan.getId(), definition.getCompletionRuleKey(), false, round, context, nativeWork, ownerLinks);
                exit = evaluate(snapshot, plan.getId(), definition.getExitRuleKey(), true, round, context, nativeWork, ownerLinks);
            } catch (RuntimeException invalidDefinition) { unknown = true; continue; }
            unknown |= completion.outcome() == RuleEvaluation.Outcome.UNKNOWN || exit.outcome() == RuleEvaluation.Outcome.UNKNOWN;
            if (!completion.matched() || !exit.matched()) continue;
            var now = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            String evidence = JsonUtils.toJsonString(new StageCompletionEvidence(round.getId(), plan.getId(), completion, exit,
                    ownerLinks.stream().map(link -> new StageCompletionEvidence.BusinessResult(link.id(),binding.getTargetContextCode(),
                            binding.getTargetObjectType(),link.objectId(),link.factVersion())).toList(),
                    cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService.freeze(round, ownerLinks), approval));
            if (stages.updateStatusIfMatch(new ProjectStageStatusUpdate(tenantId, projectId, stage.getId(), stage.getVersion(),
                    "ACTIVE", "DONE", actorId == null ? "project-rules" : actorId.toString(), now)) != 1
                    || executions.finishIfActive(new ProjectNodeExecutionMapper.Finish(tenantId, projectId, round.getId(),
                    round.getVersion(), now, evidence)) != 1) throw new IllegalStateException("STAGE_COMPLETION_VERSION_CONFLICT");
            timers.scheduleFromNode(projectId, "STAGE", stage.getId());
            audit.record(tenantId, actorId == null ? 0L : actorId, correlationId, "PROJECT_STAGE_COMPLETED", "PROJECT_STAGE", stage.getId().toString(),
                    "SUCCESS", Map.of("projectId", projectId, "executionId", round.getId(), "roundNo", round.getRoundNo(), "planVersionId", plan.getId()));
            completed++;
        }
        return new Completion(completed, unknown);
    }

    private RuleEvaluation evaluate(TemplateExecutionSnapshot snapshot, Long planId, String key, boolean optional,
                                    ProjectNodeExecutionDO round, ProjectRuntimeRuleEvaluator.Facts context,
                                    boolean nativeWork, List<TaskBusinessLinkFact> ownerLinks) {
        var program = (key == null || key.isBlank()) && optional ? compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"))
                : snapshot.getRulePrograms().get(key);
        if (program == null) throw new IllegalArgumentException("FROZEN_RULE_PROGRAM_REQUIRED");
        return rules.evaluate("plan:" + planId + ":rule:" + key + ":execution:" + round.getId(), program, leaf -> switch (leaf.predicate()) {
            case "WAIT_ELAPSED" -> facts.resolveRelativeTime(leaf, context, round.getId());
            case "STAGE_NATIVE_STATUS" -> nativeWork ? RuleFact.known(round.getSubmittedAt() != null) : RuleFact.unknown("NATIVE_COMPLETION_NOT_APPLICABLE");
            case "BUSINESS_FACT" -> leaf.parameters().has("sourceNodeKey") ? facts.resolveFact(leaf, context)
                    : ownerLinks.isEmpty() ? RuleFact.unknown("BUSINESS_LINK_GROUP_EMPTY")
                    : TaskBusinessCompletionEvaluator.businessFact(leaf,ownerLinks,new java.util.ArrayList<>(),new java.util.ArrayList<>());
            default -> facts.resolveFact(leaf,context);
        });
    }
}
