package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import org.flowable.dmn.engine.DmnEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** Real LiteFlow and non-relational DMN only; no application profiles, databases or owner services. */
class ProjectTemplateMatchRuleEvaluatorTest {
    private static RuleEngineTestFixture engine;
    private static DmnEngine dmn;
    private static ProjectTemplateMatchRuleEvaluator matching;
    private static final ProjectRuleCompiler COMPILER = new ProjectRuleCompiler();

    @BeforeAll static void start() {
        engine = new RuleEngineTestFixture();
        dmn = new ProjectDecisionEngineConfiguration().projectDecisionEngine();
        assertNull(dmn.getDmnEngineConfiguration().getDataSource());
        assertFalse(dmn.getDmnEngineConfiguration().isUsingRelationalDatabase());
        matching = new ProjectTemplateMatchRuleEvaluator(COMPILER, engine.evaluator(), new ProjectDecisionTableService(dmn));
    }

    @AfterAll static void stop() {
        if (dmn != null) dmn.close();
        if (engine != null) engine.close();
    }

    @Test void noRuleMeansUnrestrictedAndDoesNotConsultLegacyDimensions() {
        var snapshot = new TemplateExecutionSnapshot();
        snapshot.getMatch().setSigningMethod("OLD_CONDITION");
        var result = matching.evaluate(7L, 10L, snapshot, Map.of());
        assertTrue(result.matched());
        assertTrue(result.steps().contains("pmsRuleMatched"));
        assertEquals("tenant:7:template-revision:10:match:unrestricted", result.ruleVersionRef());
    }

    @Test void executesFrozenProgramNotAnotherExpressionWithTheSameRuleKey() {
        var snapshot = snapshot(field("project.projectName", "TEXT", "=", "\"工前准备\""));
        // Deliberately different source JSON proves that execution never silently recompiles it.
        snapshot.setRules(List.of(new VersionRule("applicable", "适用条件", VersionRule.Kind.CONDITION,
                false, json("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}"), null)));
        var result = matching.evaluate(7L, 10L, snapshot, Map.of("project.projectName", RuleFact.known("其他项目")));
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, result.outcome());
        assertTrue(result.steps().contains("pmsRuleNotMatched"));
        assertFalse(JsonUtils.toJsonString(result).contains("其他项目"));
        assertFalse(JsonUtils.toJsonString(result).contains("工前准备"));
    }

    @Test void missingProgramDoesNotFallBackToSourceOrUnrestricted() {
        var snapshot = snapshot(field("project.projectName", "TEXT", "=", "\"工前准备\""));
        snapshot.setRulePrograms(Map.of());
        var result = matching.evaluate(7L, 10L, snapshot, Map.of("project.projectName", RuleFact.known("工前准备")));
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, result.outcome());
        assertEquals("MATCH_PROGRAM_UNAVAILABLE", result.reasonCode());
        assertTrue(result.steps().isEmpty());
    }

    @Test void missingFactsRemainUnknownUnderNotAndBothGroupOperators() {
        String missing = field("project.businessType", "TEXT", "=", "\"DELIVERY\"");
        for (String expression : List.of("{\"operator\":\"NOT\",\"rules\":[" + missing + "]}",
                "{\"operator\":\"ANY\",\"rules\":[{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}," + missing + "]}",
                "{\"operator\":\"ALL\",\"rules\":[{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}," + missing + "]}")) {
            var result = matching.evaluate(7L, 10L, snapshot(expression), Map.of());
            assertEquals(RuleEvaluation.Outcome.UNKNOWN, result.outcome());
            assertEquals("MATCH_FIELD_UNAVAILABLE", result.conditions().getFirst().reasonCode());
        }
    }

    @Test void rejectsRuntimeOnlyFieldsEvenWhenCallerProvidesThem() {
        var snapshot = snapshot(field("project.lifecycleStatus", "TEXT", "=", "\"NORMAL_CLOSED\""));
        var result = matching.evaluate(7L, 10L, snapshot, Map.of("project.lifecycleStatus", RuleFact.known("NORMAL_CLOSED")));
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, result.outcome());
        assertEquals("MATCH_FIELD_NOT_AVAILABLE_AT_CREATION", result.conditions().getFirst().reasonCode());
    }

    @Test void knownEmptyValueIsNotConfusedWithMissingEvidence() {
        var snapshot = snapshot(field("project.majorProjectLevel", "TEXT", "null", "null"));
        assertTrue(matching.evaluate(7L, 10L, snapshot, Map.of("project.majorProjectLevel", RuleFact.known(null))).matched());
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, matching.evaluate(7L, 10L, snapshot, Map.of()).outcome());
    }

    @Test void optionalDecisionTableUsesFrozenTypedInputsAndNeverOtherwiseOnUnknown() {
        var table = decision();
        var expression = json("""
                {"predicate":"DECISION","parameters":{"table":%s,"fieldCode":"allowed",
                "valueType":"BOOLEAN","operator":"=","value":true}}
                """.formatted(JsonUtils.toJsonString(table)));
        var snapshot = snapshot(expression.toString());
        assertTrue(matching.evaluate(7L, 10L, snapshot, Map.of("project.isChild", RuleFact.known(true))).matched());
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED,
                matching.evaluate(7L, 10L, snapshot, Map.of("project.isChild", RuleFact.known(false))).outcome());
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, matching.evaluate(7L, 10L, snapshot, Map.of()).outcome());
    }

    @Test void aTypedDecisionCannotBeUsedAsABooleanWithoutOutputSelection() {
        var snapshot = new TemplateExecutionSnapshot();
        snapshot.setMatchRuleKey("applicable");
        snapshot.setRulePrograms(Map.of("applicable", COMPILER.compileDecision(
                new VersionRule("applicable", "策略", VersionRule.Kind.DECISION, false, null, decision()))));
        assertEquals("MATCH_PROGRAM_UNAVAILABLE", matching.evaluate(7L, 10L, snapshot, Map.of()).reasonCode());
    }

    @Test void sharedProgramKeepsTenantVersionAndCreationFactsIsolatedAcrossThreads() throws Exception {
        var snapshot = snapshot(field("project.isChild", "BOOLEAN", "=", "true"));
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = java.util.stream.IntStream.range(0, 40).mapToObj(index -> (Callable<Void>) () -> {
                boolean child = index % 2 == 0;
                var result = matching.evaluate((long) index, (long) index + 100, snapshot,
                        Map.of("project.isChild", RuleFact.known(child)));
                assertEquals(child, result.matched());
                assertEquals("tenant:" + index + ":template-revision:" + (index + 100) + ":match:applicable", result.ruleVersionRef());
                return null;
            }).toList();
            for (var future : pool.invokeAll(jobs)) future.get();
        }
    }

    private static TemplateExecutionSnapshot snapshot(String expression) {
        var snapshot = new TemplateExecutionSnapshot();
        snapshot.setMatchRuleKey("applicable");
        snapshot.setRules(List.of(new VersionRule("applicable", "适用条件", VersionRule.Kind.CONDITION, false, json(expression), null)));
        snapshot.setRulePrograms(Map.of("applicable", COMPILER.compile(json(expression))));
        return snapshot;
    }

    private static String field(String code, String type, String operator, String value) {
        return "{\"predicate\":\"FIELD\",\"parameters\":{\"fieldCode\":\"" + code + "\",\"valueType\":\""
                + type + "\",\"operator\":\"" + operator + "\",\"value\":" + value + "}}";
    }

    private static JsonNode json(String value) { return JsonUtils.parseObject(value, JsonNode.class); }

    private static DecisionTableDefinition decision() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="definition" name="适用策略" namespace="pms" expressionLanguage="juel">
                  <decision id="eligible" name="适用策略"><decisionTable id="table" hitPolicy="FIRST">
                    <input id="i"><inputExpression id="ie" typeRef="boolean"><text>child</text></inputExpression></input>
                    <output id="o" name="allowed" typeRef="boolean"/>
                    <rule id="yes"><inputEntry id="yesIn"><text>true</text></inputEntry><outputEntry id="yesOut"><text>true</text></outputEntry></rule>
                    <rule id="no"><inputEntry id="noIn"><text>-</text></inputEntry><outputEntry id="noOut"><text>false</text></outputEntry></rule>
                  </decisionTable></decision>
                </definitions>
                """;
        return new DecisionTableDefinition("childStrategy", "子项目适用策略", "eligible", xml, Map.of("child", "project.isChild"));
    }
}
