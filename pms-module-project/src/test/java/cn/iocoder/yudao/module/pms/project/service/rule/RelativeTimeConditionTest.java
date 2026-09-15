package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RelativeTimeConditionTest {
    static RuleEngineTestFixture engine;
    final ProjectRuleCompiler compiler = new ProjectRuleCompiler();
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }

    static tools.jackson.databind.JsonNode waitRule(String anchor, String duration, String source) {
        var parameters = new java.util.LinkedHashMap<String, String>();
        parameters.put("anchor", anchor); parameters.put("duration", duration);
        if (source != null) parameters.put("sourceNodeKey", source);
        return JsonUtils.parseTree(JsonUtils.toJsonString(Map.of("predicate", "WAIT_ELAPSED", "parameters", parameters)));
    }

    @Test void durationUsesNativeArithmeticAndMissingAnchorNeverPassesBooleanCombinations() {
        var expression = waitRule("NODE_ACTIVATED", "PT30M", null);
        Instant anchor = Instant.parse("2026-09-15T01:00:00Z");
        var program = compiler.compile(expression);
        assertEquals(false, RelativeTimeCondition.evaluate(expression.path("parameters"), anchor, anchor.plusSeconds(1799)).value());
        assertTrue(engine.evaluator().evaluate("round:1", program,
                leaf -> RelativeTimeCondition.evaluate(leaf.parameters(), anchor, anchor.plusSeconds(1800))).matched());
        for (String operator : List.of("NOT", "ALL", "ANY")) {
            var group = compiler.compile(JsonUtils.parseTree(JsonUtils.toJsonString(Map.of("operator", operator, "rules", List.of(expression)))));
            assertEquals(RuleEvaluation.Outcome.UNKNOWN, engine.evaluator().evaluate("round:2", group,
                    leaf -> RelativeTimeCondition.evaluate(leaf.parameters(), null, anchor.plusSeconds(1800))).outcome());
        }
        assertEquals(anchor.plusSeconds(86400), RelativeTimeCondition.deadline(waitRule("NODE_ACTIVATED", "P1D", null).path("parameters"), anchor));
        assertEquals(anchor, RelativeTimeCondition.deadline(waitRule("NODE_ACTIVATED", "PT0S", null).path("parameters"), anchor));
    }

    @Test void compilerRejectsNegativeCalendarDurationsInvalidAnchorsAndAmbiguousSources() {
        for (String duration : List.of("", "tomorrow", "P1M", "-PT1S"))
            assertThrows(RuntimeException.class, () -> compiler.compile(waitRule("NODE_ACTIVATED", duration, null)));
        assertThrows(RuntimeException.class, () -> compiler.compile(waitRule("CREATED", "PT1H", null)));
        assertThrows(RuntimeException.class, () -> compiler.compile(waitRule("NODE_COMPLETED", "PT1H", null)));
        assertThrows(RuntimeException.class, () -> compiler.compile(waitRule("NODE_ACTIVATED", "PT1H", "task:survey")));
    }

    @Test void simulationRequiresDatesAndUsesSameElapsedComparisonWithIndependentSourceInputs() {
        var service = new ProjectRuleSimulationService(compiler, engine.evaluator(), null);
        var expression = waitRule("NODE_COMPLETED", "PT30M", "task:survey");
        var rule = new VersionRule("wait", "工勘完成后等待", VersionRule.Kind.CONDITION, false, expression, null);
        var values = Map.of("clock.now", JsonUtils.parseTree("\"2026-09-15T01:30:00Z\""),
                "clock.completed:task:survey", JsonUtils.parseTree("\"2026-09-15T09:00:00+08:00\""));
        var result = service.simulate(7L, List.of(rule), "wait", values);
        assertTrue(((RuleEvaluation) result.evaluation()).matched());
        assertEquals(List.of("clock.now", "clock.completed:task:survey"), result.inputs().stream().map(ProjectRuleSimulationService.Input::key).toList());
        assertTrue(result.inputs().stream().allMatch(input -> "DATETIME".equals(input.valueType())));
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, ((RuleEvaluation) service.simulate(7L, List.of(rule), "wait",
                Map.of("clock.now", values.get("clock.now"))).evaluation()).outcome());
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, ((RuleEvaluation) service.simulate(7L, List.of(rule), "wait",
                Map.of("clock.now", values.get("clock.now"), "clock.completed:task:survey", JsonUtils.parseTree("true"))).evaluation()).outcome());
    }
}
