package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

class TemplateClosurePolicyTest {
    private static final String POLICY = """
            {"closureType":"NORMAL","ruleRevision":1,"requireTerminalStage":true,
             "requireAllTasksDone":true,"revalidateBusinessFacts":true,
             "processDefinitionKey":"PMS_MINIMAL_NORMAL_CLOSURE","reviewerUserId":"9223372036854775807"}
            """;

    @Test
    void optionalPolicyDoesNotChangeLegacyValidation() {
        var content = JsonUtils.parseObject("{}", TemplateDefinitionContent.class);
        assertNull(content.getClosurePolicy());
        assertTrue(TemplatePublishValidator.validateClosurePolicy(null).isEmpty());
        var previous = TemplatePublishValidator.validate(content);
        content.setClosurePolicy(parse(POLICY));
        assertEquals(previous, TemplatePublishValidator.validate(content));
    }

    @Test
    void preservesExactStringLongAndCopiesBothDirections() {
        var original = (ObjectNode) JsonUtils.parseTree(POLICY);
        var policy = new TemplateDefinitionContent.ClosurePolicy(original);
        original.put("reviewerUserId", 1);
        assertEquals(Long.MAX_VALUE, policy.getReviewerUserId());
        assertEquals(JsonUtils.parseTree(POLICY), policy.toJson());
        ((ObjectNode) policy.toJson()).put("requireAllTasksDone", false);
        assertTrue(policy.toJson().get("requireAllTasksDone").booleanValue());
        var content = JsonUtils.parseObject("{\"closurePolicy\":" + POLICY + "}", TemplateDefinitionContent.class);
        assertEquals(JsonUtils.parseTree(POLICY), JsonUtils.parseTree(JsonUtils.toJsonString(content)).get("closurePolicy"));
    }

    @Test
    void acceptsPositiveNumericLongWithoutConvertingToString() {
        var policy = parse(POLICY.replace("\"9223372036854775807\"", "9223372036854775807"));
        assertEquals(Long.MAX_VALUE, policy.getReviewerUserId());
        assertTrue(policy.toJson().get("reviewerUserId").isIntegralNumber());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "true", "null", "{}", "[]", "\"\"", "\"+1\"",
            "\"1.0\"", "\" 1\"", "9223372036854775808", "\"9223372036854775808\""})
    void rejectsInvalidReviewer(String value) {
        assertThrows(IllegalArgumentException.class, () -> parse(POLICY.replace("\"9223372036854775807\"", value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"closureType", "ruleRevision", "requireTerminalStage", "requireAllTasksDone",
            "revalidateBusinessFacts", "processDefinitionKey", "reviewerUserId"})
    void requiresEveryField(String field) {
        var node = (ObjectNode) JsonUtils.parseTree(POLICY);
        node.remove(field);
        assertThrows(IllegalArgumentException.class, () -> new TemplateDefinitionContent.ClosurePolicy(node));
    }

    @ParameterizedTest
    @ValueSource(strings = {"requireTerminalStage", "requireAllTasksDone", "revalidateBusinessFacts"})
    void rejectsWaiversAndBooleanCoercion(String field) {
        var node = (ObjectNode) JsonUtils.parseTree(POLICY);
        node.put(field, false);
        assertThrows(IllegalArgumentException.class, () -> new TemplateDefinitionContent.ClosurePolicy(node));
        node.put(field, "true");
        assertThrows(IllegalArgumentException.class, () -> new TemplateDefinitionContent.ClosurePolicy(node));
    }

    @Test
    void rejectsOtherRulesAndUnknownProperties() {
        assertThrows(IllegalArgumentException.class, () -> parse(POLICY.replace("NORMAL\"", "NO_TRACKING\"")));
        assertThrows(IllegalArgumentException.class, () -> parse(POLICY.replace(":1,", ":2,")));
        assertThrows(IllegalArgumentException.class, () -> parse(POLICY.replace(":1,", ":\"1\",")));
        assertThrows(IllegalArgumentException.class, () -> parse(POLICY.replace(":1,", ":1.0,")));
        assertThrows(IllegalArgumentException.class, () -> parse(POLICY.replace("PMS_MINIMAL_NORMAL_CLOSURE", "OTHER")));
        var node = (ObjectNode) JsonUtils.parseTree(POLICY);
        node.put("autoApproval", true);
        assertThrows(IllegalArgumentException.class, () -> new TemplateDefinitionContent.ClosurePolicy(node));
        assertThrows(RuntimeException.class, () -> JsonUtils.parseObject(
                "{\"closurePolicy\":" + node + "}", TemplateDefinitionContent.class));
    }

    private TemplateDefinitionContent.ClosurePolicy parse(String json) {
        return new TemplateDefinitionContent.ClosurePolicy(JsonUtils.parseTree(json));
    }
}
