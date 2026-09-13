package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionPayloadValidator;
import com.yomahub.liteflow.builder.el.ELBus;
import com.yomahub.liteflow.builder.el.ELWrapper;
import com.yomahub.liteflow.builder.el.AndELWrapper;
import com.yomahub.liteflow.builder.el.OrELWrapper;
import com.yomahub.liteflow.builder.el.NotELWrapper;
import com.yomahub.liteflow.builder.el.CommonNodeELWrapper;
import com.yomahub.liteflow.builder.el.IfELWrapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Compiles business rule data using the native LiteFlow builder, not a second boolean interpreter. */
@Component
public class ProjectRuleCompiler {
    public RuleProgram compile(JsonNode expression) {
        List<RuleProgram.Leaf> leaves = new ArrayList<>();
        ELWrapper condition = compileNode(expression, "rule", leaves);
        // https://liteflow.cc/pages/a3cb4b/ -- native construction validates expression operand types.
        boolean hasDecisions = leaves.stream().anyMatch(leaf -> leaf.predicate().equals("DECISION"));
        String el = (hasDecisions
                ? ELBus.then("pmsRulePrepare", "pmsRuleDecisions", resultBranch(condition))
                : ELBus.then("pmsRulePrepare", resultBranch(condition))).toEL();
        return new RuleProgram(VersionRule.Kind.CONDITION, el, leaves);
    }

    public RuleProgram compileDecision(VersionRule rule) {
        require(rule != null && rule.kind() == VersionRule.Kind.DECISION && rule.decision() != null,
                "decision rule definition required");
        JsonNode parameters = JsonUtils.parseObject(JsonUtils.toJsonString(java.util.Map.of("table", rule.decision())), JsonNode.class);
        return new RuleProgram(VersionRule.Kind.DECISION, ELBus.then("pmsRuleDecisionValue").toEL(),
                List.of(new RuleProgram.Leaf("decision", "rules." + rule.key(), "DECISION_VALUE", parameters)));
    }

    private ELWrapper compileNode(JsonNode node, String path, List<RuleProgram.Leaf> leaves) {
        require(node != null && node.isObject(), path + ": rule object required");
        if (node.has("operator")) {
            require(!node.has("predicate"), path + ": group cannot also be a predicate");
            String operator = node.path("operator").asText();
            require(Set.of("ALL", "ANY", "NOT").contains(operator), path + ": invalid group operator");
            JsonNode children = node.path("rules");
            require(children.isArray() && !children.isEmpty(), path + ": empty rule group");
            require(!operator.equals("NOT") || children.size() == 1, path + ": NOT requires exactly one rule");
            List<ELWrapper> compiled = new ArrayList<>();
            for (int i = 0; i < children.size(); i++)
                compiled.add(compileNode(children.get(i), path + ".rules[" + i + "]", leaves));
            ELWrapper[] operands = compiled.toArray(ELWrapper[]::new);
            if (operator.equals("NOT")) return negate(compiled.getFirst());
            // Native AND/OR require at least two operands; a one-child editor group is that child.
            if (operands.length == 1) return operands[0];
            return switch (operator) {
                case "ALL" -> ELBus.and((Object[]) operands);
                default -> ELBus.or((Object[]) operands);
            };
        }
        String predicate = node.path("predicate").asText();
        JsonNode parameters = node.path("parameters");
        require(parameters.isObject(), path + ": parameters required");
        if (predicate.equals("FIELD")) {
            RuleFieldComparison.validate(parameters);
        } else if (predicate.equals("CONSTANT")) {
            require(parameters.path("value").isBoolean(), path + ": boolean constant required");
        } else if (predicate.equals("DECISION")) {
            JsonNode table = parameters.path("table");
            require(table.isObject() && table.path("key").isTextual() && !table.path("key").asText().isBlank()
                    && table.path("xml").isTextual(), path + ": decision table definition required");
            RuleFieldComparison.validate(parameters);
        } else {
            require(DeliveryDefinitionPayloadValidator.PREDICATES.contains(predicate), path + ": unregistered predicate");
            if (predicate.endsWith("_NATIVE_STATUS"))
                require("DONE".equals(parameters.path("requiredStatus").asText()), path + ": requiredStatus must be DONE");
            else if (predicate.equals("BUSINESS_FACT")) {
                require(DeliveryDefinitionPayloadValidator.code(parameters.path("factCode").asText()), path + ": factCode required");
                require(Set.of("ALL", "ANY").contains(parameters.path("quantifier").asText()), path + ": quantifier required");
            } else {
                require(DeliveryDefinitionPayloadValidator.code(parameters.path("refCode").asText()), path + ": reference required");
            }
        }
        String key = "condition" + leaves.size();
        leaves.add(new RuleProgram.Leaf(key, path, predicate, parameters.deepCopy()));
        String component = predicate.equals("FIELD") || predicate.equals("DECISION") ? "pmsRuleField" : "pmsRulePredicate";
        return ELBus.element(component).tag(key);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    private static ELWrapper negate(ELWrapper operand) {
        return switch (operand) {
            case CommonNodeELWrapper node -> ELBus.not(node);
            case AndELWrapper group -> ELBus.not(group);
            case OrELWrapper group -> ELBus.not(group);
            case NotELWrapper group -> ELBus.not(group);
            default -> throw new IllegalArgumentException("boolean expression required");
        };
    }

    private static IfELWrapper resultBranch(ELWrapper operand) {
        return switch (operand) {
            case CommonNodeELWrapper node -> ELBus.ifOpt(node, "pmsRuleMatched", "pmsRuleNotMatched");
            case AndELWrapper group -> ELBus.ifOpt(group, "pmsRuleMatched", "pmsRuleNotMatched");
            case OrELWrapper group -> ELBus.ifOpt(group, "pmsRuleMatched", "pmsRuleNotMatched");
            case NotELWrapper group -> ELBus.ifOpt(group, "pmsRuleMatched", "pmsRuleNotMatched");
            default -> throw new IllegalArgumentException("boolean expression required");
        };
    }
}
