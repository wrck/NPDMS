package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler;
import cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleEvaluationService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessLinkFact;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** PM-11 / PM-03: read-only Owner facts; never executes business completion or native fallback. */
@Service
@RequiredArgsConstructor
public class TaskBusinessCompletionEvaluator {
    private final ProjectTaskBusinessService businessService;
    private final ProjectRuleCompiler compiler;
    private final ProjectRuleEvaluationService rules;

    /** Called inside the platform command transaction, after project/task/current-contract locks. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Result evaluateLocked(TaskActionCommand command, ProjectTaskExecutionContractDO contract,
                                 TaskWorkbenchActor actor) {
        if (!Objects.equals(command.executionContractId(), contract.getId())
                || !Objects.equals(command.contractVersion(), contract.getContractVersion())) {
            return failed("EXECUTION_CONTRACT_VERSION_MISMATCH");
        }
        if (command.expectedBusinessFactVersion() == null
                || !command.expectedBusinessFactVersion().matches("[0-9a-f]{64}")) {
            return failed("BUSINESS_FACT_VERSION_REQUIRED");
        }
        // The service locks every current link and invokes each Owner's lockAndRevalidate;
        // neither its cached link version nor inspect/read output is completion evidence.
        var locked = businessService.lockAndRevalidateLinkedFacts(command.taskId(), actor.tenantId(),
                actor.actorId(), actor.correlationId(), command.expectedBusinessFactVersion());
        if (locked == null || !Objects.equals(locked.factVersion(), command.expectedBusinessFactVersion())) {
            return failed("BUSINESS_FACT_VERSION_MISMATCH");
        }
        return evaluate(contract, locked.factVersion(), locked.links());
    }

    public Result evaluate(ProjectTaskExecutionContractDO contract, String factVersion, List<TaskBusinessLinkFact> links) {
        List<String> unmet = new ArrayList<>();
        List<Map<String, Object>> criteria = new ArrayList<>();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("aggregateFactVersion", factVersion);
        evidence.put("ownerContext", contract.getTargetContextCode());
        evidence.put("objectType", contract.getTargetObjectType());
        evidence.put("executionContractId", contract.getId());
        evidence.put("contractVersion", contract.getContractVersion());
        evidence.put("ruleType", contract.getCompletionRuleTypeCode());
        evidence.put("ruleSnapshot", contract.getCompletionRuleSnapshot());
        evidence.put("criteria", criteria);
        if (factVersion == null || factVersion.isBlank()) unmet.add("BUSINESS_FACT_VERSION_UNKNOWN");
        if (links == null || links.isEmpty()) unmet.add("BUSINESS_LINK_GROUP_EMPTY");
        List<Map<String, Object>> records = new ArrayList<>();
        HashSet<String> identities = new HashSet<>();
        if (links != null) for (TaskBusinessLinkFact link : links) {
            if (link == null || link.objectId() == null || link.objectId().isBlank()
                    || link.factVersion() == null || link.factVersion().isBlank()
                    || !identities.add(link.objectId())) {
                unmet.add("BUSINESS_FACT_IDENTITY_OR_VERSION_UNKNOWN");
                continue;
            }
            records.add(Map.of("linkId", link.id(), "objectId", link.objectId(),
                    "factVersion", link.factVersion()));
        }
        evidence.put("links", records);
        if (unmet.isEmpty()) {
            try {
                JsonNode snapshot = JsonUtils.parseObject(contract.getCompletionRuleSnapshot(), JsonNode.class);
                JsonNode rule = snapshot;
                if (snapshot != null && !snapshot.has("predicate") && !snapshot.has("operator")) {
                    rule = JsonUtils.parseObject(JsonUtils.toJsonString(Map.of("predicate",
                            contract.getCompletionRuleTypeCode(), "parameters", snapshot)), JsonNode.class);
                }
                var evaluation = rules.evaluate("task-contract:" + contract.getId() + ":" + contract.getContractVersion(),
                        compiler.compile(rule), leaf -> businessFact(leaf, links, criteria, unmet));
                evidence.put("ruleOutcome", evaluation.outcome());
                evidence.put("conditions", evaluation.conditions());
                evidence.put("components", evaluation.steps());
                evidence.put("diagnostics", evaluation.diagnostics());
                if (!evaluation.matched() && unmet.isEmpty()) unmet.add(evaluation.reasonCode() == null
                        ? "BUSINESS_CRITERIA_NOT_SATISFIED" : evaluation.reasonCode());
            } catch (IllegalArgumentException ex) {
                unmet.add("COMPLETION_RULE_INVALID");
            }
        }
        return new Result(unmet.isEmpty(), List.copyOf(unmet), evidence);
    }

    public static RuleFact businessFact(RuleProgram.Leaf leaf, List<TaskBusinessLinkFact> links,
                                  List<Map<String, Object>> criteria, List<String> invalid) {
        // No implicit Owner fact aliases, no native status or unsupported predicate fallback.
        if (!"BUSINESS_FACT".equals(leaf.predicate())) {
            invalid.add("BUSINESS_PREDICATE_UNSUPPORTED");
            return RuleFact.unknown("BUSINESS_PREDICATE_UNSUPPORTED");
        }
        String code = leaf.parameters().path("factCode").asText();
        String quantifier = leaf.parameters().path("quantifier").asText();
        boolean all = true, any = false;
        boolean available = true;
        List<Map<String, Object>> results = new ArrayList<>();
        for (TaskBusinessLinkFact link : links) {
            Boolean value = link.completionFacts().get(code);
            if (value == null) {
                available = false;
                invalid.add("BUSINESS_FACT_UNKNOWN:" + code + ":" + link.objectId());
            }
            boolean passed = Boolean.TRUE.equals(value);
            all &= passed;
            any |= passed;
            results.add(Map.of("objectId", link.objectId(), "factVersion", link.factVersion(),
                    "result", value == null ? "UNKNOWN" : passed ? "SATISFIED" : "NOT_SATISFIED"));
        }
        boolean result = "ALL".equals(quantifier) ? all : any;
        criteria.add(Map.of("criterion", code, "quantifier", quantifier,
                "outcome", available ? result ? "MATCHED" : "NOT_MATCHED" : "UNKNOWN", "links", results));
        return available ? RuleFact.known(result) : RuleFact.unknown("BUSINESS_FACT_UNKNOWN");
    }

    private Result failed(String code) { return new Result(false, List.of(code), Map.of()); }

    public record Result(boolean satisfied, List<String> unmetItems, Map<String, Object> evidence) {}
}
