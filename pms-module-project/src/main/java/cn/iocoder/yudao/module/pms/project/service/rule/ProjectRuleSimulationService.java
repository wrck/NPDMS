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
            if (leaf.predicate().equals("CHILD_PROJECT_WAIT")) {
                String key = inputKey(leaf);
                inputs.putIfAbsent(key, new Input(key, leaf.parameters().path("scope").asText().equals("DIRECT")
                        ? "直接子项目的关闭状态" : "全部子孙项目的关闭状态", "CHILD_PROJECT_STATUSES"));
                continue;
            }
            if (leaf.predicate().equals("WAIT_ELAPSED")) {
                inputs.putIfAbsent("clock.now", new Input("clock.now", "模拟当前时间（含时区）", "DATETIME"));
                String key = relativeAnchorKey(leaf);
                inputs.putIfAbsent(key, new Input(key, "模拟本轮起算时间（含时区）", "DATETIME"));
                continue;
            }
            if (leaf.predicate().startsWith("DECISION")) {
                var table = table(leaf);
                decisions.validate(table, table.inputFields().values().stream().collect(java.util.stream.Collectors.toSet()));
                table.inputFields().values().forEach(key -> inputs.putIfAbsent(key, new Input(key, key,
                        ProjectRuleFields.catalog().stream().filter(field -> field.code().equals(key))
                                .map(ProjectRuleFields.Field::valueType).findFirst().orElse("TEXT"))));
            } else {
                String key = inputKey(leaf);
                inputs.putIfAbsent(key, new Input(key, leaf.predicate().equals("TIME_REACHED") ? "模拟当前时间（含时区）" : key,
                        leaf.predicate().equals("TIME_REACHED") ? "DATETIME" : leaf.predicate().equals("FIELD")
                        ? leaf.parameters().path("valueType").asText() : "BOOLEAN"));
            }
        }
        RuleResult result = evaluator.evaluateRule("simulation:" + ruleKey, program, leaf -> {
            if (leaf.predicate().equals("CHILD_PROJECT_WAIT")) {
                var statuses = values.get(inputKey(leaf));
                if (statuses == null || !statuses.isArray()) return RuleFact.unknown("SIMULATION_INPUT_MISSING");
                var suppliedStatuses = new java.util.ArrayList<String>();
                for (var status : statuses) suppliedStatuses.add(status.isTextual() ? status.asText() : null);
                return cn.iocoder.yudao.module.pms.project.domain.rule.ChildProjectWaitCondition.parse(leaf.parameters())
                        .evaluate(suppliedStatuses);
            }
            if (leaf.predicate().equals("WAIT_ELAPSED")) {
                var now = supplied(values, "clock.now");
                var anchor = supplied(values, relativeAnchorKey(leaf));
                if (!now.available()) return now;
                if (!anchor.available()) return anchor;
                try {
                    return cn.iocoder.yudao.module.pms.project.domain.rule.RelativeTimeCondition.evaluate(leaf.parameters(),
                            java.time.OffsetDateTime.parse((String) anchor.value()).toInstant(),
                            java.time.OffsetDateTime.parse((String) now.value()).toInstant());
                } catch (RuntimeException invalid) {
                    return RuleFact.unknown("SIMULATION_TIME_INVALID");
                }
            }
            if (leaf.predicate().equals("TIME_REACHED")) {
                var supplied = supplied(values, inputKey(leaf));
                if (!supplied.available()) return supplied;
                try {
                    return cn.iocoder.yudao.module.pms.project.domain.rule.AbsoluteTimeCondition.evaluate(leaf.parameters(),
                            java.time.OffsetDateTime.parse((String) supplied.value()).toInstant());
                } catch (RuntimeException invalid) {
                    return RuleFact.unknown("SIMULATION_TIME_INVALID");
                }
            }
            if (!leaf.predicate().startsWith("DECISION")) return supplied(values, inputKey(leaf));
            var table = table(leaf);
            var decision = decisions.evaluate(tenantId, "simulation", table, key -> supplied(values, key));
            results.put(leaf.key(), decision);
            return decisions.select(decision, leaf);
        });
        return new Simulation(program.el(), List.copyOf(inputs.values()), result, Map.copyOf(results));
    }

    public static String inputKey(RuleProgram.Leaf leaf) {
        if (leaf.predicate().equals("CHILD_PROJECT_WAIT")) return "children:" + leaf.parameters().path("scope").asText();
        if (leaf.predicate().equals("TIME_REACHED")) return "clock.now";
        if (leaf.predicate().equals("FIELD")) return leaf.parameters().path("fieldCode").asText();
        String reference = leaf.parameters().path("refCode").asText(
                leaf.parameters().path("factCode").asText("current"));
        return leaf.predicate() + ":" + reference + (leaf.predicate().equals("BUSINESS_FACT")
                ? ":" + leaf.parameters().path("quantifier").asText()
                    + (leaf.parameters().has("sourceNodeKey") ? ":source:" + leaf.parameters().path("sourceNodeKey").asText() : "") : "");
    }

    public static String relativeAnchorKey(RuleProgram.Leaf leaf) {
        return leaf.parameters().path("anchor").asText().equals("NODE_ACTIVATED") ? "clock.activation"
                : "clock.completed:" + leaf.parameters().path("sourceNodeKey").asText();
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
