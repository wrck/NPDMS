package cn.iocoder.yudao.module.pms.project.service.rule;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessProviderRegistry;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChildProjectWaitRuleTest {
    static RuleEngineTestFixture engine;
    static final ProjectRuleCompiler compiler = new ProjectRuleCompiler();
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }
    static JsonNode expression(String scope, String types, String quantifier, boolean empty) {
        return JsonUtils.parseTree("""
                {"predicate":"CHILD_PROJECT_WAIT","parameters":{"scope":"%s","acceptedClosureTypes":%s,"quantifier":"%s","emptyResult":%s}}
                """.formatted(scope, types, quantifier, empty));
    }
    static JsonNode normal(String quantifier, boolean empty) {
        return expression("DIRECT", "[\"NORMAL_CLOSED\"]", quantifier, empty);
    }
    RuleEvaluation simulate(JsonNode rule, JsonNode values) {
        var service = new ProjectRuleSimulationService(compiler, engine.evaluator(), mock(ProjectDecisionTableService.class));
        var definition = new VersionRule("wait", "子项目等待", VersionRule.Kind.CONDITION, false, rule, null);
        return (RuleEvaluation) service.simulate(7L, List.of(definition), "wait",
                values == null ? Map.of() : Map.of("children:DIRECT", values)).evaluation();
    }
    @Test void acceptedClosureTypesAreAlternativesAndAllRequiresEveryChildToBeClosed() throws Exception {
        JsonNode rule;
        try (var input = getClass().getResourceAsStream("/project-template/child-project-wait-rule.json")) {
            assertNotNull(input);
            var sample = JsonUtils.parseObject(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), VersionRule.class);
            assertFalse(sample.shared());
            rule = sample.expression();
        }
        for (String statuses : List.of("[]", "[\"NORMAL_CLOSED\"]", "[\"EXCEPTION_CLOSED\"]",
                "[\"NORMAL_CLOSED\",\"EXCEPTION_CLOSED\"]"))
            assertTrue(simulate(rule, JsonUtils.parseTree(statuses)).matched());
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, simulate(rule,
                JsonUtils.parseTree("[\"NORMAL_CLOSED\",\"ACTIVE\"]")).outcome());
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, simulate(normal("ALL", true),
                JsonUtils.parseTree("[\"EXCEPTION_CLOSED\"]")).outcome());
    }
    @Test void anyAndExplicitEmptyResultAreIndependent() {
        assertTrue(simulate(normal("ANY", false), JsonUtils.parseTree("[\"NORMAL_CLOSED\",\"ACTIVE\"]")).matched());
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, simulate(normal("ALL", false), JsonUtils.parseTree("[]")).outcome());
        assertTrue(simulate(normal("ANY", true), JsonUtils.parseTree("[]")).matched());
    }
    @Test void missingUnrecognizedAndUnreadableFactsNeverPassAnyOrNot() {
        for (JsonNode values : java.util.Arrays.asList(null, JsonUtils.parseTree("[\"NORMAL_CLOSED\",null]"),
                JsonUtils.parseTree("[\"NORMAL_CLOSED\",\"NO_TRACKING_CLOSED\"]"))) {
            var leaf = normal("ANY", true);
            assertEquals(RuleEvaluation.Outcome.UNKNOWN, simulate(leaf, values).outcome());
            var not = JsonUtils.parseTree("{\"operator\":\"NOT\",\"rules\":[" + leaf + "]}");
            assertEquals(RuleEvaluation.Outcome.UNKNOWN, simulate(not, values).outcome());
            for (String operator : List.of("ANY", "ALL")) {
                var group = JsonUtils.parseTree("{\"operator\":\"" + operator + "\",\"rules\":[{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}," + leaf + "]}");
                assertEquals(RuleEvaluation.Outcome.UNKNOWN, simulate(group, values).outcome());
            }
        }
    }
    @Test void malformedPoliciesAreRejectedAndMatchingCannotReadRuntimeChildFacts() {
        for (String params : List.of("{}", "{\"scope\":\"DIRECT\"}"))
            assertThrows(IllegalArgumentException.class, () -> compiler.compile(JsonUtils.parseTree(
                    "{\"predicate\":\"CHILD_PROJECT_WAIT\",\"parameters\":" + params + "}")));
        for (JsonNode rule : List.of(expression("SOME", "[\"NORMAL_CLOSED\"]", "ALL", true),
                expression("DIRECT", "[]", "ALL", true), expression("DIRECT", "[\"ACTIVE\"]", "ALL", true),
                expression("DIRECT", "[\"NORMAL_CLOSED\",\"NORMAL_CLOSED\"]", "ALL", true)))
            assertThrows(IllegalArgumentException.class, () -> compiler.compile(rule));
        var document = new TemplateDesignerDocument();
        document.setRules(List.of(new VersionRule("wait", "等待", VersionRule.Kind.CONDITION, false, normal("ALL", true), null)));
        document.setMatchRuleKey("wait");
        var validator = new ProjectRulePublicationValidator(compiler, mock(ProjectDecisionTableService.class), mock(TaskBusinessProviderRegistry.class));
        assertTrue(validator.validate(document).stream().anyMatch(issue -> issue.code().equals("MATCH_REQUIRES_CREATION_FACTS")));
    }
    @Test void versionProgramsRoundTripWithoutReadingAnotherRuleSource() {
        var program = compiler.compile(normal("ALL", true));
        var frozen = JsonUtils.parseObject(JsonUtils.toJsonString(program), RuleProgram.class);
        assertEquals(program, frozen);
        var result = engine.evaluator().evaluate("parent-plan:12:wait", frozen, leaf ->
                ChildProjectWaitCondition.parse(leaf.parameters()).evaluate(List.of("NORMAL_CLOSED")));
        assertTrue(result.matched());
        assertTrue(result.steps().contains("pmsRulePredicate"));
    }
}
