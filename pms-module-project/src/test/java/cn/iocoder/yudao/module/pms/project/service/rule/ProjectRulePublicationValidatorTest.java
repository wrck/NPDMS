package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.DecisionTableDefinition;
import cn.iocoder.yudao.module.pms.project.domain.rule.VersionRule;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import com.yomahub.liteflow.flow.FlowBus;
import org.flowable.dmn.engine.DmnEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRulePublicationValidatorTest {
    private static RuleEngineTestFixture rules;
    private static DmnEngine decisions;
    private static ProjectRulePublicationValidator validator;

    @BeforeAll static void start() {
        rules = new RuleEngineTestFixture();
        decisions = new ProjectDecisionEngineConfiguration().projectDecisionEngine();
        validator = new ProjectRulePublicationValidator(new ProjectRuleCompiler(), new ProjectDecisionTableService(decisions));
    }
    @AfterAll static void stop() { if (decisions != null) decisions.close(); if (rules != null) rules.close(); }

    @Test void nativeValidationDoesNotPublishOrExecuteAChain() {
        var before = Map.copyOf(FlowBus.getChainMap());
        assertTrue(validator.validate(document(condition("""
                {"predicate":"CONSTANT","parameters":{"value":true}}
                """))).isEmpty());
        assertEquals(before, FlowBus.getChainMap());
    }

    @Test void missingComponentIsRejectedBeforePublication() {
        var component = FlowBus.getNode("pmsRuleMatched");
        FlowBus.removeNode("pmsRuleMatched");
        try {
            assertTrue(validator.validate(document(condition("""
                    {"predicate":"CONSTANT","parameters":{"value":true}}
                    """))).stream().anyMatch(issue -> issue.code().equals("RULE_COMPONENT_UNAVAILABLE")));
        } finally {
            FlowBus.getNodeMap().put("pmsRuleMatched", component);
        }
    }

    @Test void matchingOnlyUsesCreationTimeFactsAndRetainsDeclaredTypes() {
        var source = document(condition("""
                {"predicate":"FIELD","parameters":{"fieldCode":"project.lifecycleStatus","valueType":"TEXT","operator":"=","value":"ACTIVE"}}
                """));
        assertTrue(validator.validate(source).isEmpty());
        source.setMatchRuleKey("rule");
        assertTrue(validator.validate(source).stream().anyMatch(issue -> issue.code().equals("RULE_FIELD_UNAVAILABLE")));
        var wrongType = document(condition("""
                {"predicate":"FIELD","parameters":{"fieldCode":"project.isChild","valueType":"TEXT","operator":"=","value":"false"}}
                """));
        assertTrue(validator.validate(wrongType).stream().anyMatch(issue -> issue.code().equals("RULE_FIELD_TYPE_MISMATCH")));
    }

    @Test void nativeDecisionExpressionsAndOpenInputBindingsAreChecked() {
        var original = ProjectDecisionTableServiceTest.table("100", "true");
        var table = new DecisionTableDefinition(original.key(), original.name(), original.decisionKey(), original.xml(),
                Map.of("amount", "project.majorProjectLevel"));
        var rule = new VersionRule("rule", "策略", VersionRule.Kind.DECISION, false, null, table);
        assertTrue(validator.validate(document(rule)).isEmpty());
        var invalid = new DecisionTableDefinition(table.key(), table.name(), table.decisionKey(),
                table.xml().replace("<text>true</text>", "<text>${missing.invoke()}</text>"), table.inputFields());
        assertTrue(validator.validate(document(new VersionRule("rule", "策略", VersionRule.Kind.DECISION, false, null, invalid)))
                .stream().anyMatch(issue -> issue.code().equals("RULE_EXECUTION_DEFINITION_INVALID")));
        assertFalse(validator.validate(document(new VersionRule("rule", "策略", VersionRule.Kind.DECISION, false, null, original))).isEmpty());
    }

    @Test void explicitComparisonMustReferenceAnExistingDecisionOutput() {
        var original = ProjectDecisionTableServiceTest.table("100", "true");
        var table = new DecisionTableDefinition(original.key(), original.name(), original.decisionKey(), original.xml(),
                Map.of("amount", "project.majorProjectLevel"));
        var condition = condition("""
                {"predicate":"DECISION","parameters":{"ruleKey":"strategy","fieldCode":"missing","valueType":"BOOLEAN","operator":"=","value":true}}
                """);
        var source = document(condition);
        source.setRules(List.of(condition, new VersionRule("strategy", "策略", VersionRule.Kind.DECISION, false, null, table)));
        assertTrue(validator.validate(source).stream().anyMatch(issue -> issue.code().equals("DECISION_OUTPUT_UNAVAILABLE")));
    }

    private static VersionRule condition(String expression) {
        return new VersionRule("rule", "条件", VersionRule.Kind.CONDITION, false, JsonUtils.parseObject(expression, JsonNode.class), null);
    }
    private static TemplateDesignerDocument document(VersionRule rule) {
        var source = new TemplateDesignerDocument(); source.setRules(List.of(rule)); return source;
    }
}
