package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleProgram;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Real LiteFlow with its Boot 4 configuration; no datasource, Redis, owner services or production profiles. */
class ProjectRuleEvaluationServiceTest {
    private static RuleEngineTestFixture engine;
    private static ProjectRuleEvaluationService evaluator;
    private final ProjectRuleCompiler compiler = new ProjectRuleCompiler();

    @BeforeAll
    static void startEngine() {
        engine = new RuleEngineTestFixture();
        evaluator = engine.evaluator();
    }

    @AfterAll
    static void stopEngine() {
        if (engine != null) engine.close();
    }

    @Test
    void nativeEngineStartsWithoutBusinessInfrastructure() {
        var program = compile("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}");
        var invocation = new ProjectRuleInvocation(program, leaf -> RuleFact.known(true));
        var response = engine.bean(com.yomahub.liteflow.core.FlowExecutor.class)
                .execute2RespWithEL(program.el(), null, null, (Object) invocation);
        if (!response.isSuccess()) throw new AssertionError("Native LiteFlow execution failed", response.getCause());
        assertEquals(RuleEvaluation.Outcome.MATCHED, invocation.outcome);
    }

    @Test
    void executesNativeBooleanCompositionAndDoesNotConfuseChainSuccessWithMatching() {
        RuleProgram program = compile("""
                {"operator":"ALL","rules":[
                  {"predicate":"CONSTANT","parameters":{"value":true}},
                  {"operator":"NOT","rules":[{"predicate":"CONSTANT","parameters":{"value":true}}]}]}
                """);
        RuleEvaluation result = evaluator.evaluate("template:1:closure", program, leaf -> fail("constant should not load facts"));
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, result.outcome());
        assertTrue(result.steps().contains("pmsRuleNotMatched"));
        assertFalse(result.matched());
    }

    @Test
    void unavailableEvidenceCannotBeShortCircuitedOrNegatedIntoPermission() {
        for (String operator : List.of("ALL", "ANY")) {
            RuleProgram program = compile("""
                    {"operator":"%s","rules":[
                      {"predicate":"CONSTANT","parameters":{"value":true}},
                      {"operator":"NOT","rules":[{"predicate":"TASK","parameters":{"refCode":"later"}}]}]}
                    """.formatted(operator));
            var result = evaluator.evaluate("plan:2:admission", program, leaf -> RuleFact.unknown("OWNER_OFFLINE"));
            assertEquals(RuleEvaluation.Outcome.UNKNOWN, result.outcome());
            assertEquals("OWNER_OFFLINE", result.conditions().getFirst().reasonCode());
            assertFalse(result.steps().contains("pmsRuleMatched"));
        }
        assertTrue(evaluator.evaluate("independent", compile("""
                {"predicate":"CONSTANT","parameters":{"value":true}}
                """), leaf -> RuleFact.known(true)).matched());
    }

    @Test
    void invalidKnownValuesCannotHideBehindAPassingAnyBranch() {
        var program = compile("""
                {"operator":"ANY","rules":[{"predicate":"CONSTANT","parameters":{"value":true}},
                {"predicate":"FIELD","parameters":{"fieldCode":"project.amount","valueType":"NUMBER","operator":">","value":0}}]}
                """);
        var result = evaluator.evaluate("plan:2:admission", program, leaf -> RuleFact.known("not-a-number"));
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, result.outcome());
        assertEquals("FIELD_VALUE_INVALID", result.conditions().getFirst().reasonCode());
        assertFalse(result.steps().contains("pmsRuleMatched"));
    }

    @Test
    void comparesLiteralTextWithoutInterpolatingItIntoAnExpressionOrRegex() {
        assertTrue(field("TEXT", "=", "\"O'Reilly 中文\"", "O'Reilly 中文").matched());
        assertTrue(field("TEXT", "contains", "\"a.b\"", "xxa.byy").matched());
        assertFalse(field("TEXT", "contains", "\"a.b\"", "xxaZbyy").matched());
        assertFalse(field("TEXT", "=", "\"T(java.lang.System).exit(0)\"", "safe").matched());
    }

    @Test
    void preservesNumericPrecisionAndSupportsDatesSetsAndKnownNull() {
        assertTrue(field("NUMBER", "=", "\"9007199254740993\"", new BigDecimal("9007199254740993")).matched());
        assertFalse(field("NUMBER", "=", "\"9007199254740993\"", new BigDecimal("9007199254740992")).matched());
        assertTrue(field("NUMBER", "between", "[\"1.20\",\"1.30\"]", new BigDecimal("1.25")).matched());
        assertTrue(field("DATE", ">=", "\"2026-09-13\"", LocalDate.of(2026, 9, 14)).matched());
        assertTrue(field("TEXT", "in", "[\"普通\",\"重点\"]", "重点").matched());
        assertTrue(field("TEXT", "null", "null", null).matched());
        assertFalse(field("NUMBER", "<", "5", null).matched());
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, field("BOOLEAN", "=", "true", "true").outcome());
    }

    @Test
    void rejectsInvalidRulesBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> compile("{\"operator\":\"ALL\",\"rules\":[]}"));
        assertThrows(IllegalArgumentException.class, () -> compile("{\"predicate\":\"ARBITRARY_JAVA\",\"parameters\":{}}"));
        assertThrows(IllegalArgumentException.class, () -> compile("""
                {"predicate":"FIELD","parameters":{"fieldCode":"project.amount","valueType":"NUMBER","operator":"getClass","value":0}}
                """));
    }

    @Test
    void reusingOneProgramAcrossProjectsAndRoundsDoesNotShareMutableContext() throws Exception {
        var program = compile("{\"predicate\":\"TASK\",\"parameters\":{\"refCode\":\"prepare\"}}");
        List<Callable<Boolean>> calls = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            int instance = i;
            calls.add(() -> {
                boolean expected = instance % 2 == 0;
                var result = evaluator.evaluate("project:" + instance + ":round:2", program, leaf -> RuleFact.known(expected));
                return result.outcome() != RuleEvaluation.Outcome.UNKNOWN && result.matched() == expected
                        && result.ruleVersionRef().equals("project:" + instance + ":round:2");
            });
        }
        try (var pool = Executors.newFixedThreadPool(6)) {
            for (var result : pool.invokeAll(calls)) assertTrue(result.get());
        }
    }

    @Test
    void compiledProgramDoesNotFollowLaterDesignerChanges() {
        JsonNode source = JsonUtils.parseObject("""
                {"predicate":"FIELD","parameters":{"fieldCode":"project.type","valueType":"TEXT","operator":"=","value":"old"}}
                """, JsonNode.class);
        RuleProgram before = compiler.compile(source);
        ((tools.jackson.databind.node.ObjectNode) source.path("parameters")).put("value", "new");
        RuleProgram after = compiler.compile(source);
        assertTrue(evaluator.evaluate("version:1", before, leaf -> RuleFact.known("old")).matched());
        assertFalse(evaluator.evaluate("version:2", after, leaf -> RuleFact.known("old")).matched());
    }

    private RuleEvaluation field(String type, String operator, String value, Object fact) {
        var program = compile("""
                {"predicate":"FIELD","parameters":{"fieldCode":"project.field","valueType":"%s","operator":"%s","value":%s}}
                """.formatted(type, operator, value));
        return evaluator.evaluate("template:1:field", program, leaf -> RuleFact.known(fact));
    }

    private RuleProgram compile(String json) {
        return compiler.compile(JsonUtils.parseObject(json, JsonNode.class));
    }

}
