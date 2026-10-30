package cn.iocoder.yudao.module.pms.project.domain.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import tools.jackson.databind.JsonNode;

/** A terminal task completed only by the approved normal-closure transaction. */
public final class NormalClosureTaskCondition {
    public static final String PREDICATE = "NORMAL_CLOSURE_APPROVED";
    public static final String ROUTE = "/pms/project/normal-closure";

    private NormalClosureTaskCondition() { }

    /** Frozen leaf contracts store the predicate separately from its parameter snapshot. */
    public static boolean matches(ProjectTaskExecutionContractDO contract) {
        if (contract == null) return false;
        String completionRule = contract.getCompletionRuleSnapshot();
        if (PREDICATE.equals(contract.getCompletionRuleTypeCode()) && completionRule != null) {
            try {
                var parameters = JsonUtils.parseTree(completionRule);
                if (parameters != null && parameters.isObject() && parameters.isEmpty()) {
                    completionRule = JsonUtils.toJsonString(java.util.Map.of(
                            "predicate", PREDICATE, "parameters", parameters));
                }
            } catch (RuntimeException invalid) {
                return false;
            }
        }
        return matches(contract.getWorkBindingTypeCode(), contract.getBindingParameterSnapshot(), completionRule);
    }

    public static boolean matches(String bindingType, String parameters, String completionRule) {
        if (!"PAGE".equals(bindingType) || parameters == null || completionRule == null) return false;
        try {
            return ROUTE.equals(JsonUtils.parseTree(parameters).path("routePath").asText(null))
                    && approvedOnly(JsonUtils.parseTree(completionRule));
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    public static boolean approvedOnly(JsonNode rule) {
        return rule != null && rule.isObject() && !rule.has("operator")
                && PREDICATE.equals(rule.path("predicate").asText())
                && rule.path("parameters").isObject() && rule.path("parameters").isEmpty();
    }

    public static boolean approvedOnly(RuleProgram program) {
        return program != null && program.leaves().size() == 1
                && "rule".equals(program.leaves().getFirst().path())
                && PREDICATE.equals(program.leaves().getFirst().predicate());
    }
}
