package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class AbsoluteTimeConditionTest {
    static RuleEngineTestFixture engine;
    final ProjectRuleCompiler compiler = new ProjectRuleCompiler();
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }

    @Test void nativeElAndOffsetAwareComparisonAgreeBeforeAtAndAfterTheBoundary() {
        var program = compiler.compile(JsonUtils.parseTree("""
                {"operator":"ALL","rules":[
                  {"predicate":"TIME_REACHED","parameters":{"at":"2026-09-15T09:00:00+08:00"}},
                  {"predicate":"TIME_REACHED","parameters":{"at":"2026-09-15T01:00:00Z"}}
                ]}
                """));
        var before = engine.evaluator().evaluate("time", program, leaf -> AbsoluteTimeCondition.evaluate(leaf.parameters(), Instant.parse("2026-09-15T00:59:59Z")));
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, before.outcome());
        assertTrue(engine.evaluator().evaluate("time", program, leaf -> AbsoluteTimeCondition.evaluate(leaf.parameters(), Instant.parse("2026-09-15T01:00:00Z"))).matched());
        assertTrue(engine.evaluator().evaluate("time", program, leaf -> AbsoluteTimeCondition.evaluate(leaf.parameters(), Instant.parse("2026-09-16T01:00:00Z"))).matched());
    }

    @Test void invalidOrUnzonedDatesAreRejectedAndUnknownClockCannotPassNot() {
        for (String at : List.of("", "2026-02-30T09:00:00+08:00", "2026-09-15T09:00:00", "tomorrow"))
            assertThrows(RuntimeException.class, () -> compiler.compile(JsonUtils.parseTree("{\"predicate\":\"TIME_REACHED\",\"parameters\":{\"at\":\""+at+"\"}}")));
        var program = compiler.compile(JsonUtils.parseTree("""
                {"operator":"NOT","rules":[{"predicate":"TIME_REACHED","parameters":{"at":"2026-09-15T01:00:00Z"}}]}
                """));
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, engine.evaluator().evaluate("missing-time", program,
                leaf -> AbsoluteTimeCondition.evaluate(leaf.parameters(), null)).outcome());
    }

    @Test void publicationAcceptsTimeConditionsWithoutASeparateBusinessProvider() {
        var validator = new ProjectRulePublicationValidator(compiler, null,
                org.mockito.Mockito.mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry.class));
        assertTrue(validator.validateCondition(JsonUtils.parseTree("{\"predicate\":\"TIME_REACHED\",\"parameters\":{\"at\":\"2026-09-15T01:00:00Z\"}}")).isEmpty());
    }

    @Test void simulationUsesOneExplicitClockAndTheSameComparisonNotAnOverrideBoolean() {
        var service = new ProjectRuleSimulationService(compiler, engine.evaluator(), null);
        var expression = JsonUtils.parseTree("{\"predicate\":\"TIME_REACHED\",\"parameters\":{\"at\":\"2026-09-15T09:00:00+08:00\"}}");
        var rule = new VersionRule("due", "到达交付时间", VersionRule.Kind.CONDITION, false, expression, null);
        var result = service.simulate(7L, List.of(rule), "due", Map.of("clock.now",JsonUtils.parseTree("\"2026-09-15T01:00:00Z\"")));
        assertTrue(((RuleEvaluation) result.evaluation()).matched());
        assertEquals(List.of(new ProjectRuleSimulationService.Input("clock.now","模拟当前时间（含时区）","DATETIME")), result.inputs());
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, ((RuleEvaluation) service.simulate(7L,List.of(rule),"due",Map.of()).evaluation()).outcome());
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, ((RuleEvaluation) service.simulate(7L,List.of(rule),"due",Map.of("clock.now",JsonUtils.parseTree("true"))).evaluation()).outcome());
    }
}
