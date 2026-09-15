package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.ConditionStatus;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleEvaluationService;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** PM-03/PM-11: read-only three-valued frozen CompletionRule evaluation. Unknown never becomes false. */
@Component @RequiredArgsConstructor
public class ProjectRuntimeRuleEvaluator {
    @jakarta.annotation.Resource
    private ProjectRelativeTimeFacts relativeTime;
    @jakarta.annotation.Resource
    private ProjectChildWaitFacts childWait;
    private final ProjectStageGateProviderRegistry providers;
    private final ProjectRuleCompiler compiler;
    private final ProjectRuleEvaluationService evaluator;
    private final cn.iocoder.yudao.module.pms.project.service.rule.ProjectDecisionTableService decisions;
    private final cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService businessSources;

    public record Facts(ProjectMasterDO project, ProjectStageInstanceDO stage, List<ProjectTaskInstanceDO> tasks,
                        List<ProjectGateInstanceDO> gates, List<ProjectGateReferenceInstanceDO> references,
                        boolean stageCompletion) { }

    public ConditionStatus evaluate(JsonNode rule, Facts facts) {
        try {
            var program = compiler.compile(rule);
            var result = evaluate("project:" + facts.project().getId() + ":graph:"
                    + facts.stage().getGraphVersion() + ":stage:" + facts.stage().getId(), program, facts);
            return switch (result.outcome()) {
                case MATCHED -> ConditionStatus.SATISFIED;
                case NOT_MATCHED -> ConditionStatus.UNSATISFIED;
                case UNKNOWN -> ConditionStatus.UNAVAILABLE;
            };
        } catch (RuntimeException unavailable) {
            return ConditionStatus.UNAVAILABLE;
        }
    }

    public cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation evaluate(
            String ruleVersionReference, RuleProgram program, Facts facts) {
        return evaluator.evaluate(ruleVersionReference, program, leaf -> resolveFact(leaf, facts));
    }

    public RuleFact resolveFact(RuleProgram.Leaf leaf, Facts facts) {
        String predicate = leaf.predicate();
        if ("CHILD_PROJECT_WAIT".equals(predicate)) return childWait.resolve(facts.project(), leaf.parameters());
        if ("WAIT_ELAPSED".equals(predicate)) return resolveRelativeTime(leaf, facts, null);
        if ("TIME_REACHED".equals(predicate))
            return cn.iocoder.yudao.module.pms.project.domain.rule.AbsoluteTimeCondition.evaluate(leaf.parameters(), java.time.Instant.now());
        if ("BUSINESS_FACT".equals(predicate)) return businessSources.resolve(facts.project(), leaf);
        if ("DECISION".equals(predicate))
            return decisions.resolve(facts.project().getTenantId(), "project:" + facts.project().getId()
                    + ":plan:" + facts.project().getActivePlanVersionId(), leaf,
                    code -> cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields.read(facts.project(), code));
        if ("FIELD".equals(predicate))
            return cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields.read(
                    facts.project(), leaf.parameters().path("fieldCode").asText());
        if ("STAGE_NATIVE_STATUS".equals(predicate)) {
            if (facts.stage() == null) return RuleFact.unknown("STAGE_CONTEXT_REQUIRED");
            if (!facts.stageCompletion()) return RuleFact.known("DONE".equals(facts.stage().getStatus()));
            // Native readiness is prospective completion, never a pre-write of the stage status.
            return RuleFact.known(facts.tasks().stream().filter(t -> Objects.equals(t.getStageCode(), facts.stage().getStageCode()))
                    .allMatch(t -> "DONE".equals(t.getStatus())));
        }
        if ("TASK_NATIVE_STATUS".equals(predicate)) return RuleFact.unknown("TASK_CONTEXT_REQUIRED");
        String refCode = leaf.parameters().path("refCode").asText();
        String key = ProjectStageReadinessService.providerKey(predicate);
        if (key == null || !providers.hasProvider(key)) return RuleFact.unknown("FACT_PROVIDER_UNAVAILABLE");
        List<ProjectGateReferenceInstanceDO> matches = facts.references().stream()
                .filter(r -> predicate.equals(r.getRefType()) && refCode.equals(r.getRefCode()))
                .filter(r -> facts.gates().stream().anyMatch(g -> Objects.equals(g.getId(), r.getGateId())
                        && (facts.stage() == null || Objects.equals(g.getStageCode(), facts.stage().getStageCode())))).toList();
        // BPM business identity is the actual frozen reference. Never invent gate/reference IDs.
        if (("PROCESS".equals(predicate) || "APPROVAL".equals(predicate)) && matches.size() != 1)
            return RuleFact.unknown("APPROVAL_REFERENCE_UNAVAILABLE");
        ProjectGateReferenceInstanceDO ref = matches.size() == 1 ? matches.getFirst() : null;
        ProjectGateInstanceDO gate = ref == null ? null : facts.gates().stream()
                .filter(g -> Objects.equals(g.getId(), ref.getGateId())).findFirst().orElseThrow();
        var query = new ProjectStageGateFactQuery(facts.project().getTenantId(), facts.project().getId(),
                gate != null ? gate.getStageCode() : facts.stage() == null ? null : facts.stage().getStageCode(), gate == null ? null : gate.getId(),
                gate == null ? null : gate.getGateCode(), gate == null ? null : gate.getVersion(),
                ref == null ? null : ref.getId(), ref == null ? null : ref.getVersion(), predicate, refCode,
                ref == null ? null : ref.getRefVersion(), null);
        ProjectStageGateFact fact = providers.lockAndRevalidate(key, query);
        return switch (fact.outcome()) {
            case SATISFIED -> RuleFact.known(true);
            case UNSATISFIED -> RuleFact.known(false);
            default -> RuleFact.unknown("FACT_UNAVAILABLE");
        };
    }

    public RuleFact resolveRelativeTime(RuleProgram.Leaf leaf, Facts facts, Long executionId) {
        return relativeTime.resolve(facts.project().getTenantId(), facts.project().getId(), executionId, leaf.parameters());
    }
}
