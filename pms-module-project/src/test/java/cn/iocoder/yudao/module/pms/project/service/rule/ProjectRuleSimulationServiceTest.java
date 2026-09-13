package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionRuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import org.flowable.dmn.engine.DmnEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRuleSimulationServiceTest {
    private static RuleEngineTestFixture rules;
    private static DmnEngine decisions;
    private static ProjectRuleSimulationService service;

    @BeforeAll static void start() {
        rules = new RuleEngineTestFixture();
        decisions = new ProjectDecisionEngineConfiguration().projectDecisionEngine();
        service = new ProjectRuleSimulationService(new ProjectRuleCompiler(), rules.evaluator(), new ProjectDecisionTableService(decisions));
    }
    @AfterAll static void stop() { if (decisions != null) decisions.close(); if (rules != null) rules.close(); }

    @Test void decisionRulesKeepCategoricalValuesAndConditionRulesCanUseThemExplicitly() {
        var original = ProjectDecisionTableServiceTest.table("100", "true");
        var xml = original.xml().replace("typeRef=\"boolean\"", "typeRef=\"string\"")
                .replace("<text>true</text>", "<text>\"FAST\"</text>").replace("<text>false</text>", "<text>\"NORMAL\"</text>");
        var table = new DecisionTableDefinition("route", "交付分类", "eligibility", xml, original.inputFields());
        var decision = new VersionRule("route", "交付分类", VersionRule.Kind.DECISION, true, null, table);
        var condition = new VersionRule("complete", "完成判断", VersionRule.Kind.CONDITION, false, json("""
                {"predicate":"DECISION","parameters":{"ruleKey":"route","fieldCode":"allowed","valueType":"TEXT","operator":"=","value":"FAST","quantifier":"ANY"}}
                """), null);
        var inputs = Map.of("project.amount", json("120"));
        var raw = service.simulate(7L, List.of(decision, condition), "route", inputs);
        var result = assertInstanceOf(DecisionRuleEvaluation.class, raw.evaluation());
        assertEquals(DecisionRuleEvaluation.Status.AVAILABLE, result.status());
        assertEquals("FAST", result.values().getFirst().get("allowed"));
        assertEquals("DECISION", json(JsonUtils.toJsonString(raw.evaluation())).path("kind").asText());
        var check = service.simulate(7L, List.of(decision, condition), "complete", inputs);
        assertTrue(assertInstanceOf(RuleEvaluation.class, check.evaluation()).matched());
        assertTrue(check.evaluation().steps().contains("pmsRuleDecisions"));
    }

    @Test void absentDecisionInputsStayUnknownForBothResultKinds() {
        var decision = new VersionRule("rule", "策略", VersionRule.Kind.DECISION, false, null, ProjectDecisionTableServiceTest.table("100", "true"));
        var response = service.simulate(7L, List.of(decision), "rule", Map.of());
        var result = assertInstanceOf(DecisionRuleEvaluation.class, response.evaluation());
        assertEquals(DecisionRuleEvaluation.Status.UNKNOWN, result.status());
        assertTrue(result.values().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(item -> "project.amount".equals(item.inputKey())
                && "SIMULATION_INPUT_MISSING".equals(item.code())));
        assertTrue(result.diagnostics().stream().anyMatch(item -> "pmsRuleDecisionValue".equals(item.component())));
    }

    private static JsonNode json(String json) { return JsonUtils.parseObject(json, JsonNode.class); }
}
