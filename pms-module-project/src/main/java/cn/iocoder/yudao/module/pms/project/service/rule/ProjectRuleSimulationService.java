package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleResult;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Simulated values never become business facts or authorization evidence. */
@Service
@RequiredArgsConstructor
public class ProjectRuleSimulationService {
    private final ProjectRuleCompiler compiler;
    private final ProjectRuleEvaluationService evaluator;
    private final ProjectDecisionTableService decisions;

    public record Input(String key, String label, String valueType) { }
    public record Simulation(String el, List<Input> inputs, RuleResult evaluation,
                             Map<String, ProjectDecisionTableService.Result> decisions) { }

    public Simulation simulate(Long tenantId, List<VersionRule> versionRules, String ruleKey, Map<String, JsonNode> values) {
        var index = TemplateRuleCollection.index(versionRules);
        var definition = index.get(ruleKey);
        if (definition == null) throw new IllegalArgumentException("当前版本不存在规则: " + ruleKey);
        RuleProgram program = definition.kind() == VersionRule.Kind.CONDITION
                ? compiler.compile(TemplateRuleCollection.condition(index, ruleKey)) : compiler.compileDecision(definition);
        Map<String, Input> inputs = new LinkedHashMap<>();
        Map<String, ProjectDecisionTableService.Result> results = new LinkedHashMap<>();
        for (var leaf : program.leaves()) {
            if (leaf.predicate().equals("CONSTANT")) continue;
            if (leaf.predicate().startsWith("DECISION")) {
                var table = table(leaf);
                decisions.validate(table, table.inputFields().values().stream().collect(java.util.stream.Collectors.toSet()));
                table.inputFields().values().forEach(key -> inputs.putIfAbsent(key, new Input(key, key,
                        ProjectRuleFields.catalog().stream().filter(field -> field.code().equals(key))
                                .map(ProjectRuleFields.Field::valueType).findFirst().orElse("TEXT"))));
            } else {
                String key = inputKey(leaf);
                inputs.putIfAbsent(key, new Input(key, key, leaf.predicate().equals("FIELD")
                        ? leaf.parameters().path("valueType").asText() : "BOOLEAN"));
            }
        }
        RuleResult result = evaluator.evaluateRule("simulation:" + ruleKey, program, leaf -> {
            if (!leaf.predicate().startsWith("DECISION")) return supplied(values, inputKey(leaf));
            var table = table(leaf);
            var decision = decisions.evaluate(tenantId, "simulation", table, key -> supplied(values, key));
            results.put(leaf.key(), decision);
            return decisions.select(decision, leaf);
        });
        return new Simulation(program.el(), List.copyOf(inputs.values()), result, Map.copyOf(results));
    }

    public static String inputKey(RuleProgram.Leaf leaf) {
        if (leaf.predicate().equals("FIELD")) return leaf.parameters().path("fieldCode").asText();
        String reference = leaf.parameters().path("refCode").asText(
                leaf.parameters().path("factCode").asText("current"));
        return leaf.predicate() + ":" + reference + (leaf.predicate().equals("BUSINESS_FACT")
                ? ":" + leaf.parameters().path("quantifier").asText() : "");
    }

    private static DecisionTableDefinition table(RuleProgram.Leaf leaf) {
        return JsonUtils.convertObject(leaf.parameters().path("table"), DecisionTableDefinition.class);
    }

    private static RuleFact supplied(Map<String, JsonNode> values, String key) {
        if (!values.containsKey(key)) return RuleFact.unknown("SIMULATION_INPUT_MISSING");
        JsonNode value = values.get(key);
        if (value == null || value.isNull()) return RuleFact.known(null);
        if (value.isBoolean()) return RuleFact.known(value.booleanValue());
        if (value.isNumber()) return RuleFact.known(value.decimalValue());
        if (value.isTextual()) return RuleFact.known(value.asText());
        return RuleFact.unknown("SIMULATION_INPUT_TYPE_INVALID");
    }
}
