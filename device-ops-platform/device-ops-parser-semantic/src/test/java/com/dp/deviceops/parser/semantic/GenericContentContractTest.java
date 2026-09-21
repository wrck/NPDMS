package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericContentContractTest {

    @Test
    void serializesFlattenedSectionPayloadAndNestedUnitIdentity() throws Exception {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeValue", "in use");
        node.put("children", Map.of("read community", "[REDACTED]"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entries", List.of(Map.of(
                "key", "snmp v1",
                "value", "in use",
                "startLine", 1,
                "endLine", 3,
                "children", List.of())));
        payload.put("data", Map.of("snmp v1", node));
        GenericContent.Section section = new GenericContent.Section(
                1, "keyValueTree", 1, 3, List.of(), payload, List.of());
        GenericContent content = new GenericContent(List.of(
                new GenericContent.Unit(1, null, null, 0, "show version", 1, 3,
                        GenericContent.StructureStatus.STRUCTURED, List.of(section), List.of(), null),
                new GenericContent.Unit(3, 3, 7, 1, "show interface", 349, 1349,
                        GenericContent.StructureStatus.EMPTY, List.of(), List.of(), null)));
        SemanticParseResult result = new SemanticParseResult(
                "1.1.0", "1.3.0", "1.3.0", "1.3.0",
                "a".repeat(64), "b".repeat(64), "c".repeat(64),
                Map.of(), content, Map.of(),
                new ModelProfileSelection("generic", null,
                        ModelProfileSelection.Source.GENERIC, null, null, List.of()),
                Map.of(), List.of(), List.of());

        JsonNode json = new CanonicalJson().mapper().readTree(new CanonicalJson().bytes(result));

        assertEquals("1.1.0", result.schemaVersion());
        assertEquals("keyValueTree",
                json.at("/genericContent/units/0/sections/0/type").textValue());
        assertTrue(json.at("/genericContent/units/0/sections/0/entries").isArray());
        assertFalse(json.at("/genericContent/units/0/sections/0").has("payload"));
        assertFalse(json.at("/genericContent/units/0").has("parentCommandIndex"));
        assertFalse(json.at("/genericContent/units/0").has("sectionIndex"));
        assertEquals(3, json.at("/genericContent/units/1/parentCommandIndex").intValue());
        assertEquals(7, json.at("/genericContent/units/1/sectionIndex").intValue());
        assertEquals("in use", json.at(
                "/genericContent/units/0/sections/0/data/snmp v1/nodeValue").textValue());
    }

    @Test
    void legacyResultOmitsGenericContent() throws Exception {
        SemanticParseResult legacy = new SemanticParseResult(
                "1.0.0", "1.0.0", "1.0.0", "1.0.0",
                "a".repeat(64), "b".repeat(64), "c".repeat(64),
                Map.of(), Map.of(), Map.of(), List.of());

        JsonNode json = new CanonicalJson().mapper().readTree(new CanonicalJson().bytes(legacy));

        assertFalse(json.has("genericContent"));
    }
}
