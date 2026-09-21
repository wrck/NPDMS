package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.core.model.CommandBlockStatus;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.internal.SemanticLimits;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSemanticParserTest {

    @Test
    void producesDeviceProjectionAndTraceableQualityFromCommandBlocks() {
        SemanticParseResult result = DefaultSemanticParser.bundled().parse(List.of(
                block(1, "show version", CommandBlockStatus.SUCCEEDED,
                        "\u001B[31mSoftware Release TEST-1.2.3\u001B[0m\n"
                                + "Conplat Version is PLATFORM-9\n"
                                + "CPU type: Fictional CPU 1000\n"
                                + "SDRAM: 1024M bytes\n"
                                + "Serial Number: SYNTHETIC-SN-001", false),
                block(2, "show ip interface brief", CommandBlockStatus.SUCCEEDED,
                        "Interface  Physical  Protocol  IP Address\n"
                                + "port1      up        up        192.0.2.1/24", false),
                block(3, "show ntp status", CommandBlockStatus.SUCCEEDED,
                        "NTP is not enabled.", false),
                block(4, "show unknown", CommandBlockStatus.FAILED, "", false),
                block(5, "show version", CommandBlockStatus.SUCCEEDED,
                        "Software Release PARTIAL-9", true)),
                DefaultSemanticParser.bundledSpecification());

        Map<String, Object> basic = map(result.projections().get("deviceBasic"));
        assertEquals("SYNTHETIC-SN-001", basic.get("sn"));
        assertEquals("TEST-1.2.3", basic.get("softVersion"));
        assertEquals("Fictional CPU 1000", basic.get("cpuModel"));
        assertEquals(1_073_741_824L, basic.get("memoryBytes"));
        assertEquals("1.0.0", result.ruleVersion());
        assertEquals(64, result.inputSha256().length());
        assertEquals(ObservationStatus.NOT_ENABLED, result.observations().get(2).status());
        assertEquals(ObservationStatus.EXECUTION_FAILED, result.observations().get(3).status());
        assertEquals(ObservationStatus.PARTIAL, result.observations().get(4).status());
        assertEquals(List.of("TERMINAL_CONTROL_REMOVED"), result.observations().getFirst().warnings());
        assertEquals(1, result.observations().getFirst().sourceLineStart());
        assertEquals(4L, result.quality().get("mappedBlockCount"));
        assertFalse(result.snapshot().toString().contains("\u001B"));
    }

    @Test
    void returnsIdenticalBytesForIdenticalEvidenceAndCatalogs() {
        DefaultSemanticParser parser = DefaultSemanticParser.bundled();
        SemanticParserSpecification specification = DefaultSemanticParser.bundledSpecification();
        List<CommandOutputBlock> blocks = List.of(block(1, "show version", CommandBlockStatus.SUCCEEDED,
                "Software Release SAME\nSerial Number: SAME-SN", false));
        CanonicalJson json = new CanonicalJson();

        byte[] first = json.bytes(parser.parse(blocks, specification));
        byte[] second = json.bytes(parser.parse(blocks, specification));
        byte[] third = json.bytes(parser.parse(blocks, specification));

        assertEquals(java.util.HexFormat.of().formatHex(first), java.util.HexFormat.of().formatHex(second));
        assertEquals(java.util.HexFormat.of().formatHex(first), java.util.HexFormat.of().formatHex(third));
    }

    @Test
    void redactsSensitiveCandidatesAndDoesNotPublishFacts() {
        String rules = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","rules":[{
                  "ruleId":"secret","blockRole":"SECRET","roleSelectors":[{"type":"CONTENT_REGEX","value":"password"}],
                  "extractors":[{"type":"KEY_VALUE","key":"password","transforms":["trim"]}],
                  "target":{"semanticKey":"device.identity.secret","cardinality":"ONE","dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}
                }]}
                """;
        String projections = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","profiles":[{
                  "projectionId":"secret","missingValuePolicy":"NULL","fields":{"value":"device.identity.secret"}
                }]}
                """;

        SemanticParseResult result = DefaultSemanticParser.bundled().parse(
                List.of(block(1, "show synthetic", CommandBlockStatus.SUCCEEDED,
                        "password: forbidden-value", false)),
                new SemanticParserSpecification(rules, projections));

        assertNull(map(result.projections().get("secret")).get("value"));
        assertEquals(ObservationStatus.REDACTED, result.observations().getFirst().status());
        assertFalse(new CanonicalJson().text(result).contains("forbidden-value"));
    }

    @Test
    void rejectsUnsafeProjectionAndResourceOverflow() {
        String unsafe = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","profiles":[{
                  "projectionId":"unsafe","missingValuePolicy":"NULL","fields":{"value":"device.__proto__.value"}
                }]}
                """;
        SemanticParserSpecification bundled = DefaultSemanticParser.bundledSpecification();
        SemanticParserError unsafeError = assertThrows(SemanticParserError.class,
                () -> DefaultSemanticParser.bundled().parse(List.of(),
                        new SemanticParserSpecification(bundled.ruleCatalogJson(), unsafe)));
        assertEquals(SemanticParserError.INVALID_PROJECTIONS, unsafeError.code());

        DefaultSemanticParser limited = new DefaultSemanticParser(
                new SemanticLimits(2_000, 500, 2_048, 1, 1_024,
                        2_048, 100, 4_096));
        SemanticParserError limitError = assertThrows(SemanticParserError.class,
                () -> limited.parse(List.of(
                        block(1, "one", CommandBlockStatus.SUCCEEDED, "one", false),
                        block(2, "two", CommandBlockStatus.SUCCEEDED, "two", false)), bundled));
        assertEquals(SemanticParserError.RESOURCE_LIMIT, limitError.code());
    }

    @Test
    void inputAndRuleHashesChangeIndependently() {
        DefaultSemanticParser parser = DefaultSemanticParser.bundled();
        SemanticParserSpecification bundled = DefaultSemanticParser.bundledSpecification();
        List<CommandOutputBlock> blocks = List.of(block(1, "show version", CommandBlockStatus.SUCCEEDED,
                "Software Release A", false));
        SemanticParseResult first = parser.parse(blocks, bundled);
        SemanticParseResult changedInput = parser.parse(List.of(block(1, "show version",
                CommandBlockStatus.SUCCEEDED, "Software Release B", false)), bundled);
        SemanticParserSpecification changedRules = new SemanticParserSpecification(
                bundled.ruleCatalogJson().replace("\"catalogVersion\": \"1.0.0\"",
                        "\"catalogVersion\": \"1.0.1\""),
                bundled.projectionCatalogJson());
        SemanticParseResult changedCatalog = parser.parse(blocks, changedRules);

        assertNotEquals(first.inputSha256(), changedInput.inputSha256());
        assertEquals(first.ruleSha256(), changedInput.ruleSha256());
        assertEquals(first.inputSha256(), changedCatalog.inputSha256());
        assertNotEquals(first.ruleSha256(), changedCatalog.ruleSha256());
    }

    @Test
    void extractsStableObjectsAndReportsOneMissingRequiredField() {
        String rules = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","rules":[{
                  "ruleId":"modules","blockRole":"INVENTORY",
                  "roleSelectors":[{"type":"COMMAND_ALIAS","value":"show inventory"}],
                  "extractors":[{"type":"LINE_REGEX_OBJECT",
                  "pattern":"^SLOT (?<slot>[0-9]+)(?: (?<model>[^ ]+))?(?: SN=(?<serial>[^ ]+))?$",
                  "fields":{"slot":{"group":"slot","transforms":["integer"]},
                  "model":{"group":"model","transforms":["trim"]},
                  "serial":{"group":"serial","transforms":["trim"],"required":false}}}],
                  "target":{"semanticKey":"device.hardware.modules","cardinality":"MANY",
                  "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}
                }]}
                """;
        SemanticParseResult result = parseWithRules(rules, block(1, "show inventory",
                CommandBlockStatus.SUCCEEDED,
                "SLOT 1 MODEL-A SN=SER-1\nSLOT 2 MODEL-B\nSLOT 3", false));

        List<?> modules = (List<?>) map(map(map(result.snapshot().get("entities")).get("device"))
                .get("hardware")).get("modules");
        assertEquals(2, modules.size());
        assertEquals(1L, map(map(modules.get(0)).get("value")).get("slot"));
        assertEquals("MODEL-B", map(map(modules.get(1)).get("value")).get("model"));
        List<?> warnings = (List<?>) map(result.snapshot().get("quality")).get("warnings");
        assertEquals(1, warnings.size());
        assertEquals("MISSING_OBJECT_FIELD", map(warnings.getFirst()).get("code"));
    }

    @Test
    void cleansConfigurationStanzasAndSplitsDelimitedDiagnostics() {
        String rules = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","rules":[
                 {"ruleId":"run","blockRole":"RUNNING_CONFIGURATION",
                  "roleSelectors":[{"type":"COMMAND_ALIAS","value":"show run"}],
                  "extractors":[{"type":"CONFIG_STANZA"}],
                  "target":{"semanticKey":"configuration.stanzas","cardinality":"MANY",
                  "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}},
                 {"ruleId":"tech","blockRole":"TECHNICAL_DIAGNOSTICS",
                  "roleSelectors":[{"type":"COMMAND_ALIAS","value":"show tech"}],
                  "extractors":[{"type":"DELIMITED_SECTION",
                  "startPattern":"^[*]{8,} *(?<command>.+?) *[*]{8,}$","headerGroup":"command"}],
                  "target":{"semanticKey":"technicalDiagnostics.sections","cardinality":"MANY",
                  "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}}
                ]}
                """;
        SemanticParseResult result = parseWithRules(rules,
                block(1, "show run", CommandBlockStatus.SUCCEEDED,
                        "show run\nGenerated configuration\n!\nhostname edge\n!\ninterface eth0\n description uplink\n!\nedge#", false),
                block(2, "show tech", CommandBlockStatus.SUCCEEDED,
                        "show tech\n******** show version ********\nv1\n"
                                + "******** show run ********\nconfig\n"
                                + "******** show health ********\nhealthy", false));

        Map<String, Object> entities = map(result.snapshot().get("entities"));
        List<?> stanzas = (List<?>) map(entities.get("configuration")).get("stanzas");
        assertEquals(2, stanzas.size());
        assertEquals("hostname edge", map(map(stanzas.getFirst()).get("value")).get("header"));
        assertFalse(stanzas.toString().contains("Generated configuration"));
        assertFalse(stanzas.toString().contains("edge#"));

        List<?> sections = (List<?>) map(entities.get("technicalDiagnostics")).get("sections");
        assertEquals(3, sections.size());
        assertEquals("show version", map(map(sections.getFirst()).get("value")).get("command"));
        assertEquals(2, map(map(sections.getFirst()).get("value")).get("startLine"));
        assertTrue(map(map(sections.getLast()).get("value")).get("lines").toString().contains("healthy"));

        SemanticParseResult fallback = parseWithRules(rules, block(1, "show tech",
                CommandBlockStatus.SUCCEEDED, "show tech\nunstructured diagnostic", false));
        List<?> fallbackSections = (List<?>) map(map(fallback.snapshot().get("entities"))
                .get("technicalDiagnostics")).get("sections");
        assertEquals(1, fallbackSections.size());
        assertEquals("show tech", map(map(fallbackSections.getFirst()).get("value")).get("command"));
    }

    @Test
    void doesNotClassifyAnAggregateDiagnosticAsDisabledFromOneNestedLine() {
        String rules = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","rules":[{
                  "ruleId":"tech","blockRole":"TECHNICAL_DIAGNOSTICS",
                  "roleSelectors":[{"type":"COMMAND_ALIAS","value":"show tech"}],
                  "extractors":[{"type":"DELIMITED_SECTION",
                  "startPattern":"^[*]{8,} *(?<command>.+?) *[*]{8,}$","headerGroup":"command"}],
                  "target":{"semanticKey":"technicalDiagnostics.sections","cardinality":"MANY",
                  "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}
                }]}
                """;
        SemanticParseResult result = parseWithRules(rules, block(1, "show tech",
                CommandBlockStatus.SUCCEEDED,
                "******** show feature-a ********\nfeature a enabled\n"
                        + "******** show feature-b ********\nfeature b is not enabled\n"
                        + "******** show health ********\nhealthy", false));

        assertEquals(ObservationStatus.OBSERVED, result.observations().getFirst().status());
    }

    @Test
    void extractsReadableDiagnosticSectionsDespiteReplacementCharacterNoise() {
        String rules = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","rules":[{
                  "ruleId":"tech","blockRole":"TECHNICAL_DIAGNOSTICS",
                  "roleSelectors":[{"type":"COMMAND_ALIAS","value":"show tech"}],
                  "extractors":[{"type":"DELIMITED_SECTION",
                  "startPattern":"^[*]{8,} *(?<command>.+?) *[*]{8,}$","headerGroup":"command"}],
                  "target":{"semanticKey":"technicalDiagnostics.sections","cardinality":"MANY",
                  "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}
                }]}
                """;
        SemanticParseResult result = parseWithRules(rules, block(1, "show tech",
                CommandBlockStatus.SUCCEEDED,
                "noise \uFFFD text\n******** show health ********\nhealthy", false));

        assertEquals(ObservationStatus.PARTIAL, result.observations().getFirst().status());
        assertTrue(result.observations().getFirst().warnings()
                .contains("SOURCE_REPLACEMENT_CHARACTER"));
        List<?> sections = (List<?>) map(map(result.snapshot().get("entities"))
                .get("technicalDiagnostics")).get("sections");
        assertTrue(sections.stream().map(DefaultSemanticParserTest::map)
                .map(section -> map(section.get("value")))
                .anyMatch(section -> "show health".equals(section.get("command"))));
    }

    private static SemanticParseResult parseWithRules(String rules, CommandOutputBlock... blocks) {
        String projections = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","profiles":[]}
                """;
        return DefaultSemanticParser.bundled().parse(List.of(blocks),
                new SemanticParserSpecification(rules, projections));
    }

    private static CommandOutputBlock block(
            int index, String command, CommandBlockStatus status, String stdout, boolean truncated) {
        return new CommandOutputBlock(index, command, status, stdout, "", stdout.length(), 1,
                truncated, status == CommandBlockStatus.SUCCEEDED ? 0 : 1, "synthetic",
                Map.of(), List.of(), null, null, false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
