package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionRuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import org.flowable.dmn.engine.DmnEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Native LiteFlow and non-relational Flowable DMN; no application profile or business datasource. */
class ProjectDecisionRuleIntegrationTest {
    private static RuleEngineTestFixture rules;
    private static DmnEngine engine;
    private static ProjectDecisionTableService decisions;
    private static ProjectRuleSimulationService simulation;
    private static final ProjectRuleCompiler COMPILER = new ProjectRuleCompiler();

    @BeforeAll static void start() {
        rules = new RuleEngineTestFixture();
        engine = new ProjectDecisionEngineConfiguration().projectDecisionEngine();
        assertNull(engine.getDmnEngineConfiguration().getDataSource());
        assertFalse(engine.getDmnEngineConfiguration().isUsingRelationalDatabase());
        decisions = new ProjectDecisionTableService(engine);
        simulation = new ProjectRuleSimulationService(COMPILER, rules.evaluator(), decisions);
    }

    @AfterAll static void stop() {
        if (engine != null) engine.close();
        if (rules != null) rules.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RULE ORDER", "COLLECT"})
    void multipleNumericOutputsKeepTheirTypesAndRequireExplicitQuantification(String policy) {
        var table = numericRule(policy, "1.25", "2.5");
        var trial = simulation.simulate(7L, List.of(table), table.key(), Map.of("project.amount", json("120")));
        var values = assertInstanceOf(DecisionRuleEvaluation.class, trial.evaluation());
        assertEquals(DecisionRuleEvaluation.Status.AVAILABLE, values.status());
        assertEquals(List.of(new BigDecimal("1.25"), new BigDecimal("2.5")), values.values().stream()
                .map(row -> new BigDecimal(assertInstanceOf(Number.class, row.get("allowed")).toString())).sorted().toList());
        var runtime = assertInstanceOf(DecisionRuleEvaluation.class, rules.evaluator().evaluateRule("plan:7:node:A:round:2",
                COMPILER.compileDecision(table), leaf -> decisions.resolve(7L, "plan:7", leaf, key -> RuleFact.known(120))));
        assertEquals(DecisionRuleEvaluation.Status.AVAILABLE, runtime.status());
        // COLLECT does not promise row order; preserve every typed row without imposing an order.
        assertEquals(values.values().size(), runtime.values().size());
        assertTrue(runtime.values().containsAll(values.values()));
        if (policy.equals("RULE ORDER")) assertEquals(values.values(), runtime.values());
        assertEquals("plan:7:node:A:round:2", runtime.ruleVersionRef());
        assertCondition(table, "ANY", "allowed", false, RuleEvaluation.Outcome.MATCHED);
        assertCondition(table, "ALL", "allowed", false, RuleEvaluation.Outcome.NOT_MATCHED);
        assertCondition(table, "", "allowed", false, RuleEvaluation.Outcome.UNKNOWN);
        assertCondition(table, "ANY", "missing", true, RuleEvaluation.Outcome.UNKNOWN);
    }

    @Test void collectSumUsesTheNativeNumericAggregation() {
        // Native hit policy, not a sum computed in project code.
        // https://www.flowable.com/open-source/docs/dmn/ch06-DMN-Introduction/#hit-policy
        var source = numericRule("COLLECT", "1.25", "2.5").decision();
        var table = new VersionRule("numeric", "数值求和策略", VersionRule.Kind.DECISION, false, null,
                new DecisionTableDefinition(source.key(), source.name(), source.decisionKey(),
                        source.xml().replace("hitPolicy=\"COLLECT\"", "hitPolicy=\"COLLECT\" aggregation=\"SUM\""), source.inputFields()));
        var response = simulation.simulate(7L, List.of(table), table.key(), Map.of("project.amount", json("120")));
        var result = assertInstanceOf(DecisionRuleEvaluation.class, response.evaluation());
        assertEquals(DecisionRuleEvaluation.Status.AVAILABLE, result.status());
        assertEquals(1, result.values().size());
        assertEquals(0, new BigDecimal("3.75").compareTo(new BigDecimal(
                assertInstanceOf(Number.class, result.values().getFirst().get("allowed")).toString())));
        assertCondition(table, "", "allowed", false, RuleEvaluation.Outcome.MATCHED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNIQUE", "ANY"})
    void conflictingHitsStayUnknownEvenUnderNegation(String policy) {
        var table = numericRule(policy, "1.25", "2.5");
        var response = simulation.simulate(7L, List.of(table), table.key(), Map.of("project.amount", json("120")));
        var result = assertInstanceOf(DecisionRuleEvaluation.class, response.evaluation());
        assertEquals(DecisionRuleEvaluation.Status.UNKNOWN, result.status());
        assertEquals("DECISION_EXECUTION_FAILED", result.reasonCode());
        assertTrue(result.values().isEmpty());
        assertCondition(table, "ANY", "allowed", true, RuleEvaluation.Outcome.UNKNOWN);
    }

    @Test void identicalRuleKeysAndElRemainIsolatedAcrossConcurrentVersionsAndProjects() throws Exception {
        var first = COMPILER.compileDecision(numericRule("FIRST", "1.25", "2.5"));
        var second = COMPILER.compileDecision(numericRule("FIRST", "7.5", "9.25"));
        assertEquals(first.el(), second.el());
        List<Callable<Void>> calls = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            int execution = i;
            calls.add(() -> {
                var program = execution % 2 == 0 ? first : second;
                var amount = execution % 3 == 0 ? 0 : 120;
                String reference = "project:" + execution + ":plan:" + (execution % 2) + ":round:2";
                var result = assertInstanceOf(DecisionRuleEvaluation.class, rules.evaluator().evaluateRule(reference,
                        program, leaf -> decisions.resolve((long) execution + 1, reference, leaf, key -> RuleFact.known(amount))));
                assertEquals(DecisionRuleEvaluation.Status.AVAILABLE, result.status());
                assertEquals(reference, result.ruleVersionRef());
                String expected = execution % 2 == 0 ? (amount == 0 ? "2.5" : "1.25") : (amount == 0 ? "9.25" : "7.5");
                assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(
                        assertInstanceOf(Number.class, result.values().getFirst().get("allowed")).toString())));
                return null;
            });
        }
        try (var pool = Executors.newFixedThreadPool(4)) {
            for (var result : pool.invokeAll(calls)) result.get();
        }
    }

    private static void assertCondition(VersionRule table, String quantifier, String output, boolean negate,
                                        RuleEvaluation.Outcome expected) {
        String expression = """
                {"predicate":"DECISION","parameters":{"ruleKey":"numeric","fieldCode":"%s",
                "valueType":"NUMBER","operator":">=","value":2,"quantifier":"%s"}}
                """.formatted(output, quantifier);
        if (negate) expression = "{\"operator\":\"NOT\",\"rules\":[" + expression + "]}";
        var condition = new VersionRule("complete", "完成判断", VersionRule.Kind.CONDITION, false, json(expression), null);
        var definitions = List.of(table, condition);
        var simulated = assertInstanceOf(RuleEvaluation.class, simulation.simulate(7L, definitions, "complete",
                Map.of("project.amount", json("120"))).evaluation());
        var program = COMPILER.compile(TemplateRuleCollection.condition(TemplateRuleCollection.index(definitions), "complete"));
        var runtime = rules.evaluator().evaluate("plan:7:complete", program,
                leaf -> decisions.resolve(7L, "plan:7", leaf, key -> RuleFact.known(120)));
        assertEquals(expected, simulated.outcome());
        assertEquals(expected, runtime.outcome());
        assertEquals(simulated.reasonCode(), runtime.reasonCode());
    }

    private static VersionRule numericRule(String policy, String first, String otherwise) {
        var source = ProjectDecisionTableServiceTest.table("100", first);
        String xml = source.xml().replace("hitPolicy=\"FIRST\"", "hitPolicy=\"" + policy + "\"")
                .replace("typeRef=\"boolean\"", "typeRef=\"number\"")
                .replace("<text>false</text>", "<text>" + otherwise + "</text>");
        var table = new DecisionTableDefinition("numeric", "数值策略", source.decisionKey(), xml, source.inputFields());
        return new VersionRule("numeric", "数值策略", VersionRule.Kind.DECISION, true, null, table);
    }

    private static JsonNode json(String value) { return JsonUtils.parseObject(value, JsonNode.class); }
}
