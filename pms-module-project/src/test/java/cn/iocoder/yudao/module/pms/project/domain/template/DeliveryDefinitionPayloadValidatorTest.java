package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** PM-03: closed payloads, no expression engine, required quantities cannot be bypassed. */
class DeliveryDefinitionPayloadValidatorTest {
    @Test void nativeBindingRequiresExplicitStrategyAndObjectMapping() {
        validate(DeliveryDefinitionKind.WORK_BINDING, """
                {"bindingType":"STAGE_NATIVE","instanceResolutionStrategy":"REFERENCE_EXISTING","contextMapping":{}}
                """);
        assertThrows(IllegalArgumentException.class, () -> validate(DeliveryDefinitionKind.WORK_BINDING, "{}"));
        assertThrows(IllegalArgumentException.class, () -> validate(DeliveryDefinitionKind.WORK_BINDING, """
                {"bindingType":"TASK_NATIVE","instanceResolutionStrategy":"REFERENCE_EXISTING","contextMapping":{},"targetObjectKey":"foreign"}
                """));
    }
    @Test void businessViewReferenceAcceptsLosslessLongWireIdButRejectsInvalidValues() {
        String binding = """
                {"bindingType":"BUSINESS_COMPONENT","instanceResolutionStrategy":"REFERENCE_EXISTING",
                 "businessViewRevisionId":"2097373775105802242","targetContextCode":"SOL",
                 "targetObjectType":"REQUIREMENT_ANALYSIS","targetObjectKey":"PROJECT_REQUIREMENT_ANALYSIS",
                 "contextMapping":{"project":"project"}}
                """;
        validate(DeliveryDefinitionKind.WORK_BINDING, binding);
        for (String invalid : List.of("0", "-1", "01", "1e3", "9223372036854775808")) {
            assertThrows(IllegalArgumentException.class, () -> validate(DeliveryDefinitionKind.WORK_BINDING,
                    binding.replace("2097373775105802242", invalid)));
        }
    }

    @Test void allAnyOnlyComposeRegisteredPredicates() {
        validate(DeliveryDefinitionKind.COMPLETION_RULE, """
                {"operator":"ALL","rules":[{"predicate":"TASK_NATIVE_STATUS","parameters":{"requiredStatus":"DONE"}},
                {"operator":"ANY","rules":[{"predicate":"STATE","parameters":{"refCode":"S0_COMPLETED"}}]}]}
                """);
    }
    @ParameterizedTest @ValueSource(strings={
            "{\"script\":\"true\"}",
            "{\"operator\":\"ALL\",\"rules\":[]}",
            "{\"predicate\":\"UNKNOWN\",\"parameters\":{}}",
            "{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\",\"sql\":\"x\"}}"})
    void unsafeOrUnknownRulesRejected(String json) {
        assertThrows(IllegalArgumentException.class, () -> validate(DeliveryDefinitionKind.COMPLETION_RULE, json));
    }
    @Test void requiredZeroQuantityRejectedButOptionalZeroAllowed() {
        String json = """
                {"scope":"STAGE","deliverableType":"REPORT","required":false,"minimumQuantity":0,
                "allowedSources":["FILE"],"outputType":"FILE","confirmationRule":{"predicate":"STATE","parameters":{"refCode":"S0_COMPLETED"}}}
                """;
        validate(DeliveryDefinitionKind.DELIVERABLE, json);
        assertThrows(IllegalArgumentException.class, () -> validate(DeliveryDefinitionKind.DELIVERABLE, json.replace("false", "true")));
    }
    @Test void exactDefaultSlotsAndDuplicateReferencesChecked() {
        JsonNode stage = json("""
                {"name":"开始","stageCode":"S0","start":true,"terminal":true,
                "workBinding":"work","permissionPolicy":"permission","completionRule":"completion"}
                """);
        var refs = List.of(new DeliveryDefinitionReference("work", 1L), new DeliveryDefinitionReference("permission", 2L),
                new DeliveryDefinitionReference("completion", 3L));
        assertDoesNotThrow(() -> DeliveryDefinitionPayloadValidator.validate(DeliveryDefinitionKind.STAGE, 1, stage, refs));
        assertThrows(IllegalArgumentException.class, () -> DeliveryDefinitionPayloadValidator.validate(DeliveryDefinitionKind.STAGE, 1, stage,
                List.of(new DeliveryDefinitionReference("work", 1L), new DeliveryDefinitionReference("work", 2L))));
        assertThrows(IllegalArgumentException.class, () -> DeliveryDefinitionPayloadValidator.validate(DeliveryDefinitionKind.STAGE, 2, stage, refs));
    }
    private void validate(DeliveryDefinitionKind kind, String source) {
        DeliveryDefinitionPayloadValidator.validate(kind, 1, json(source), List.of());
    }
    private JsonNode json(String source) { return JsonUtils.parseObject(source, JsonNode.class); }
}
