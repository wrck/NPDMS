package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionPayloadValidator;
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
                DeliveryDefinitionPayloadValidator.rule(rule);
                boolean satisfied = evaluateRule(rule, links, criteria, unmet);
                if (!satisfied && unmet.isEmpty()) unmet.add("BUSINESS_CRITERIA_NOT_SATISFIED");
            } catch (IllegalArgumentException ex) {
                unmet.add("COMPLETION_RULE_INVALID");
            }
        }
        return new Result(unmet.isEmpty(), List.copyOf(unmet), evidence);
    }

    private boolean evaluateRule(JsonNode rule, List<TaskBusinessLinkFact> links,
                                 List<Map<String, Object>> criteria, List<String> invalid) {
        if (rule.has("operator")) {
            boolean all = true, any = false;
            for (JsonNode child : rule.path("rules")) {
                boolean result = evaluateRule(child, links, criteria, invalid);
                all &= result;
                any |= result;
            }
            return "ALL".equals(rule.path("operator").asText()) ? all : any;
        }
        // No implicit Owner fact aliases, no native status or unsupported predicate fallback.
        if (!"BUSINESS_FACT".equals(rule.path("predicate").asText())) {
            invalid.add("BUSINESS_PREDICATE_UNSUPPORTED");
            return false;
        }
        String code = rule.path("parameters").path("factCode").asText();
        String quantifier = rule.path("parameters").path("quantifier").asText();
        boolean all = true, any = false;
        List<Map<String, Object>> results = new ArrayList<>();
        for (TaskBusinessLinkFact link : links) {
            Boolean value = link.completionFacts().get(code);
            if (value == null) invalid.add("BUSINESS_FACT_UNKNOWN:" + code + ":" + link.objectId());
            boolean passed = Boolean.TRUE.equals(value);
            all &= passed;
            any |= passed;
            results.add(Map.of("objectId", link.objectId(), "factVersion", link.factVersion(),
                    "result", value == null ? "UNKNOWN" : passed ? "SATISFIED" : "NOT_SATISFIED"));
        }
        boolean result = "ALL".equals(quantifier) ? all : any;
        criteria.add(Map.of("criterion", code, "quantifier", quantifier, "satisfied", result, "links", results));
        return result;
    }

    private Result failed(String code) { return new Result(false, List.of(code), Map.of()); }

    public record Result(boolean satisfied, List<String> unmetItems, Map<String, Object> evidence) {}
}
