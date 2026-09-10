package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver.ConditionStatus;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionPayloadValidator;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** PM-03/PM-11: read-only three-valued frozen CompletionRule evaluation. Unknown never becomes false. */
@Component @RequiredArgsConstructor
public class ProjectRuntimeRuleEvaluator {
    private final ProjectStageGateProviderRegistry providers;

    public record Facts(ProjectMasterDO project, ProjectStageInstanceDO stage, List<ProjectTaskInstanceDO> tasks,
                        List<ProjectGateInstanceDO> gates, List<ProjectGateReferenceInstanceDO> references,
                        boolean stageCompletion) { }

    public ConditionStatus evaluate(JsonNode rule, Facts facts) {
        try {
            DeliveryDefinitionPayloadValidator.rule(rule);
            return evaluateValidated(rule, facts);
        } catch (RuntimeException unavailable) {
            return ConditionStatus.UNAVAILABLE;
        }
    }

    private ConditionStatus evaluateValidated(JsonNode rule, Facts facts) {
        if (rule.has("operator")) {
            List<ConditionStatus> children = new ArrayList<>();
            for (JsonNode child : rule.path("rules")) children.add(evaluateValidated(child, facts));
            // Do not short circuit either ALL or ANY: unavailable evidence blocks the whole decision.
            if (children.contains(ConditionStatus.UNAVAILABLE)) return ConditionStatus.UNAVAILABLE;
            boolean satisfied = "ALL".equals(rule.path("operator").asText())
                    ? children.stream().allMatch(s -> s == ConditionStatus.SATISFIED)
                    : children.contains(ConditionStatus.SATISFIED);
            return satisfied ? ConditionStatus.SATISFIED : ConditionStatus.UNSATISFIED;
        }
        String predicate = rule.path("predicate").asText();
        if ("STAGE_NATIVE_STATUS".equals(predicate)) {
            if (!facts.stageCompletion()) return status("DONE".equals(facts.stage().getStatus()));
            // Native readiness is prospective completion, never a pre-write of the stage status.
            return status(facts.tasks().stream().filter(t -> Objects.equals(t.getStageCode(), facts.stage().getStageCode()))
                    .allMatch(t -> "DONE".equals(t.getStatus())));
        }
        if ("TASK_NATIVE_STATUS".equals(predicate)) return ConditionStatus.UNAVAILABLE; // no task identity on a stage rule
        String refCode = rule.path("parameters").path("refCode").asText();
        String key = ProjectStageReadinessService.providerKey(predicate);
        if (key == null || !providers.hasProvider(key)) return ConditionStatus.UNAVAILABLE;
        List<ProjectGateReferenceInstanceDO> matches = facts.references().stream()
                .filter(r -> predicate.equals(r.getRefType()) && refCode.equals(r.getRefCode()))
                .filter(r -> facts.gates().stream().anyMatch(g -> Objects.equals(g.getId(), r.getGateId())
                        && Objects.equals(g.getStageCode(), facts.stage().getStageCode()))).toList();
        // BPM business identity is the actual frozen reference. Never invent gate/reference IDs.
        if (("PROCESS".equals(predicate) || "APPROVAL".equals(predicate)) && matches.size() != 1)
            return ConditionStatus.UNAVAILABLE;
        ProjectGateReferenceInstanceDO ref = matches.size() == 1 ? matches.getFirst() : null;
        ProjectGateInstanceDO gate = ref == null ? null : facts.gates().stream()
                .filter(g -> Objects.equals(g.getId(), ref.getGateId())).findFirst().orElseThrow();
        var query = new ProjectStageGateFactQuery(facts.project().getTenantId(), facts.project().getId(),
                facts.project().getCurrentStage(), gate == null ? null : gate.getId(),
                gate == null ? null : gate.getGateCode(), gate == null ? null : gate.getVersion(),
                ref == null ? null : ref.getId(), ref == null ? null : ref.getVersion(), predicate, refCode);
        ProjectStageGateFact fact = providers.lockAndRevalidate(key, query);
        return switch (fact.outcome()) {
            case SATISFIED -> ConditionStatus.SATISFIED;
            case UNSATISFIED -> ConditionStatus.UNSATISFIED;
            default -> ConditionStatus.UNAVAILABLE;
        };
    }

    private static ConditionStatus status(boolean satisfied) {
        return satisfied ? ConditionStatus.SATISFIED : ConditionStatus.UNSATISFIED;
    }
}
