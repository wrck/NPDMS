package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionConfiguration;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateRuleCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateExecutionConfigurationTest {
    static final String SUBSCRIPTION = """
            {"subscriptions":[{"key":"survey","ownerContext":"SOL","entityType":"SITE_SURVEY","resultType":"CONFIRMED",
            "scope":{"mode":"OBJECTS","objectIds":["9007199254740993","9223372036854775807"]},
            "policy":{"acquisition":"REUSE_EXISTING","validity":"CURRENT_VALID","selection":"ALL_EXPECTED"}}]}
            """;
    static final String OPERATION = """
            {"operations":[{"ownerContext":"SOL","entityType":"SITE_SURVEY","permissionCode":"pms:eng-site-survey:update",
            "pre":{"mode":"RULE","ruleKey":"ready"},"post":{"mode":"NONE"}}]}
            """;

    @Test
    void subscriptionDoesNotNeedAnOperationBindingOrPageAndPreservesStringIds() {
        var source = TemplateVersionSnapshotTest.designer();
        var value = JsonUtils.parseTree(SUBSCRIPTION);
        source.getStages().getFirst().setExecution(value);
        source.getStages().getFirst().setWorkBinding(null);
        var config = TemplateExecutionConfiguration.read(value);
        assertTrue(config.operations().isEmpty());
        assertNull(config.presentation());
        assertEquals(List.of("9007199254740993", "9223372036854775807"), config.subscriptions().getFirst().scope().objectIds());
        var reopened = TemplateRuleCollection.forEditing(source);
        assertEquals(value, reopened.getStages().getFirst().getExecution());
        assertNotSame(value, reopened.getStages().getFirst().getExecution());
        assertThrows(UnsupportedOperationException.class, () -> config.subscriptions().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.subscriptions().getFirst().scope().objectIds().clear());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{\"policyHash\":\"x\"}", "{\"operations\":null}",
            "{\"subscriptions\":null}", "{\"presentation\":null}",
            "{\"operations\":[null]}", "{\"operations\":[{}]}",
            "{\"presentation\":{\"pageUrl\":4}}", "{\"presentation\":{\"pageUrl\":\"/x\",\"query\":{\"id\":1}}}"})
    void rejectsUnknownFieldsAndCoercionRatherThanDiscardingThem(String json) {
        assertThrows(IllegalArgumentException.class, () -> TemplateExecutionConfiguration.read(JsonUtils.parseTree(json)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "NULL", "EMPTY", "NONE_WITH_RULE", "RULE_WITHOUT_KEY", "HASH", "VERSION", "CLASS"})
    void missingCheckIsNotNoneAndTechnicalInputsAreRejected(String damage) {
        ObjectNode value = (ObjectNode) JsonUtils.parseTree(OPERATION);
        ObjectNode operation = (ObjectNode) value.path("operations").get(0);
        switch (damage) {
            case "MISSING" -> operation.remove("pre");
            case "NULL" -> operation.putNull("pre");
            case "EMPTY" -> operation.set("pre", JsonUtils.parseTree("{}"));
            case "NONE_WITH_RULE" -> operation.set("pre", JsonUtils.parseTree("{\"mode\":\"NONE\",\"ruleKey\":\"ready\"}"));
            case "RULE_WITHOUT_KEY" -> operation.set("pre", JsonUtils.parseTree("{\"mode\":\"RULE\"}"));
            case "HASH" -> operation.put("semanticDigest", "x");
            case "VERSION" -> operation.put("operationVersion", 1);
            case "CLASS" -> operation.put("inputClass", "java.lang.Runtime");
            default -> throw new AssertionError(damage);
        }
        assertThrows(IllegalArgumentException.class, () -> TemplateExecutionConfiguration.read(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DUPLICATE", "NUMERIC_ID", "EMPTY_SET", "PIN_MISSING", "PIN_UNUSED", "UNKNOWN_VALIDITY", "UNBOUNDED_ALL"})
    void rejectsIncompleteEvidenceIdentityAndExpectedSets(String damage) {
        ObjectNode value = (ObjectNode) JsonUtils.parseTree(SUBSCRIPTION);
        ObjectNode subscription = (ObjectNode) value.path("subscriptions").get(0);
        ObjectNode scope = (ObjectNode) subscription.get("scope");
        ObjectNode policy = (ObjectNode) subscription.get("policy");
        switch (damage) {
            case "DUPLICATE" -> ((tools.jackson.databind.node.ArrayNode) value.get("subscriptions")).add(subscription.deepCopy());
            case "NUMERIC_ID" -> scope.set("objectIds", JsonUtils.parseTree("[9007199254740993]"));
            case "EMPTY_SET" -> scope.set("objectIds", JsonUtils.parseTree("[]"));
            case "PIN_MISSING" -> policy.put("acquisition", "PINNED_RESULT");
            case "PIN_UNUSED" -> policy.put("pinnedResultId", "result-1");
            case "UNKNOWN_VALIDITY" -> policy.put("validity", "CURRENT_OR_PAST");
            case "UNBOUNDED_ALL" -> subscription.set("scope", JsonUtils.parseTree("{\"mode\":\"PROJECT\"}"));
            default -> throw new AssertionError(damage);
        }
        assertThrows(IllegalArgumentException.class, () -> TemplateExecutionConfiguration.read(value));
    }

    @Test
    void ruleReferencesParticipateInTheExistingVersionCollection() {
        var source = TemplateVersionSnapshotTest.designer();
        ObjectNode value = (ObjectNode) JsonUtils.parseTree(OPERATION);
        source.getTasks().getFirst().setExecution(value);
        assertDoesNotThrow(() -> TemplateRuleCollection.forEditing(source));
        ((ObjectNode) value.path("operations").get(0).path("pre")).put("ruleKey", "missing");
        assertThrows(IllegalArgumentException.class, () -> TemplateRuleCollection.forEditing(source));
        ((ObjectNode) value.path("operations").get(0).path("pre")).put("ruleKey", "decision");
        assertThrows(IllegalArgumentException.class, () -> TemplateRuleCollection.forEditing(source));
    }

    @Test
    void knownPolicyModesRemainDistinctAndQueryValuesAreNotExpandedWhileSaving() {
        ObjectNode value = (ObjectNode) JsonUtils.parseTree(SUBSCRIPTION);
        ObjectNode policy = (ObjectNode) value.path("subscriptions").get(0).path("policy");
        for (String mode : List.of("REUSE_EXISTING", "NEW_RESULT", "PINNED_RESULT")) {
            policy.put("acquisition", mode);
            if (mode.equals("PINNED_RESULT")) policy.put("pinnedResultId", "9007199254740999");
            else policy.remove("pinnedResultId");
            assertEquals(mode, TemplateExecutionConfiguration.read(value).subscriptions().getFirst().policy().acquisition());
        }
        value.set("presentation", JsonUtils.parseTree("{\"pageUrl\":\"/pms/delivery-business/site-survey\",\"query\":{\"projectId\":\"$project.id\",\"name\":\"含 空格&符号\"}}"));
        var presentation = TemplateExecutionConfiguration.read(value).presentation();
        assertEquals("$project.id", presentation.query().get("projectId"));
        assertEquals("含 空格&符号", presentation.query().get("name"));
        assertThrows(UnsupportedOperationException.class, () -> presentation.query().put("id", "x"));
    }
}
