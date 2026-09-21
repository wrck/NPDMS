package com.dp.deviceops.parser.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.parser.semantic.cli.CommandOutputBlockInput;
import com.dp.deviceops.parser.semantic.cli.SemanticParserInput;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundleCodec;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticParserGoldenTest {

    @Test
    void matchesReviewedGoldenResultAndIsByteStableAcrossThreeRuns() throws Exception {
        CanonicalJson canonicalJson = new CanonicalJson();
        ObjectMapper mapper = canonicalJson.mapper();
        SemanticParserInput input = mapper.readValue(resource("command-output-blocks.json").toFile(),
                SemanticParserInput.class);
        List<CommandOutputBlock> blocks = input.commandBlocks().stream()
                .map(CommandOutputBlockInput::toDomain).toList();
        DefaultSemanticParser parser = DefaultSemanticParser.bundled();
        SemanticParserSpecification specification = DefaultSemanticParser.bundledSpecification();

        byte[] first = canonicalJson.bytes(parser.parse(blocks, specification));
        byte[] second = canonicalJson.bytes(parser.parse(blocks, specification));
        byte[] third = canonicalJson.bytes(parser.parse(blocks, specification));

        assertArrayEquals(Files.readAllBytes(resource("expected-structured-result.json")), first);
        assertArrayEquals(first, second);
        assertArrayEquals(first, third);
        assertEquals(sha256(first), sha256(second));
        assertEquals(sha256(first), sha256(third));
    }

    @Test
    void projectionChangeDoesNotChangeInputOrRuleHash() {
        DefaultSemanticParser parser = DefaultSemanticParser.bundled();
        SemanticParserSpecification bundled = DefaultSemanticParser.bundledSpecification();
        List<CommandOutputBlock> blocks = List.of(new CommandOutputBlock(
                1, "show version", com.dp.deviceops.core.model.CommandBlockStatus.SUCCEEDED,
                "Software Release TEST", "", 21, 1, false, 0, "complete",
                java.util.Map.of(), List.of(), null, null, false));
        SemanticParseResult baseline = parser.parse(blocks, bundled);
        String changedProjection = bundled.projectionCatalogJson()
                .replace("\"catalogVersion\": \"1.0.0\"", "\"catalogVersion\": \"1.0.1\"")
                .replace("\"softVersion\": \"device.software.version\"",
                        "\"release\": \"device.software.version\"");
        SemanticParseResult changed = parser.parse(blocks,
                new SemanticParserSpecification(bundled.ruleCatalogJson(), changedProjection));

        assertEquals(baseline.inputSha256(), changed.inputSha256());
        assertEquals(baseline.ruleSha256(), changed.ruleSha256());
        assertNotEquals(baseline.projectionSha256(), changed.projectionSha256());
        assertNotEquals(baseline.projections(), changed.projections());
    }

    @Test
    void modelAwareThreeCommandReleaseMatchesFrozenResultsAcrossThreeRuns() throws Exception {
        Path releaseDirectory = Path.of("..", "parser-releases", "device-command-output-1.1.0")
                .toAbsolutePath().normalize();
        var bundle = new ParserReleaseBundleCodec().decode(releaseDirectory);
        var plan = new ParserPlanCompiler().compile(bundle);
        DefaultDynamicSemanticParser parser = new DefaultDynamicSemanticParser();
        CanonicalJson json = new CanonicalJson();

        for (var verificationCase : bundle.verificationCases()) {
            SemanticParseResult firstResult = parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                    verificationCase.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            byte[] first = json.bytes(firstResult);
            byte[] second = json.bytes(parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                    verificationCase.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            byte[] third = json.bytes(parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                    verificationCase.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8))));

            assertArrayEquals(verificationCase.expectedResultJson().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8), first, verificationCase.caseId());
            assertArrayEquals(first, second, verificationCase.caseId());
            assertArrayEquals(first, third, verificationCase.caseId());
            assertThreeCommandResult(verificationCase.caseId(), firstResult);
        }
    }

    @Test
    void nestedReleaseMatchesFrozenFullAndTechOnlyResultsAcrossThreeRuns() throws Exception {
        Path releaseDirectory = Path.of("..", "parser-releases", "device-command-output-1.2.0")
                .toAbsolutePath().normalize();
        var bundle = new ParserReleaseBundleCodec().decode(releaseDirectory);
        var plan = new ParserPlanCompiler().compile(bundle);
        DefaultDynamicSemanticParser parser = new DefaultDynamicSemanticParser();
        CanonicalJson json = new CanonicalJson();

        for (var verificationCase : bundle.verificationCases()) {
            SemanticParseResult firstResult = parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                    verificationCase.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            byte[] first = json.bytes(firstResult);
            byte[] second = json.bytes(parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                    verificationCase.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            byte[] third = json.bytes(parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                    verificationCase.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8))));

            assertArrayEquals(verificationCase.expectedResultJson().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8), first, verificationCase.caseId());
            assertArrayEquals(first, second, verificationCase.caseId());
            assertArrayEquals(first, third, verificationCase.caseId());
            assertEquals("vpn1000-ga-family", firstResult.profileSelection().profileId());
            assertEquals("VPN1000-GA-X", map(firstResult.projections().get("deviceBasic")).get("model"));
            assertTrue(map(firstResult.projections().get("deviceBasic")).get("softVersion") != null);
            assertEquals(51, list(map(firstResult.projections().get("technicalDiagnostics"))
                    .get("sections")).size());
            assertTrue(firstResult.nestedObservations().stream()
                    .anyMatch(observation -> "DEVICE_VERSION".equals(observation.blockRole())));
            assertTrue(firstResult.nestedObservations().stream()
                    .anyMatch(observation -> observation.status() == ObservationStatus.UNPARSED));
        }
    }

    @Test
    void structuredReleaseMatchesFrozenGenericResultsAcrossThreeRuns() throws Exception {
        Path releaseDirectory = Path.of("..", "parser-releases", "device-command-output-1.3.0")
                .toAbsolutePath().normalize();
        var bundle = new ParserReleaseBundleCodec().decode(releaseDirectory);
        var plan = new ParserPlanCompiler().compile(bundle);
        DefaultDynamicSemanticParser parser = new DefaultDynamicSemanticParser();
        CanonicalJson json = new CanonicalJson();

        for (var verificationCase : bundle.verificationCases()) {
            SemanticParseResult firstResult = parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                    verificationCase.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            byte[] first = json.bytes(firstResult);
            byte[] second = json.bytes(parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                    verificationCase.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            byte[] third = json.bytes(parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                    verificationCase.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8))));

            assertArrayEquals(verificationCase.expectedResultJson().getBytes(
                    java.nio.charset.StandardCharsets.UTF_8), first, verificationCase.caseId());
            assertArrayEquals(first, second, verificationCase.caseId());
            assertArrayEquals(first, third, verificationCase.caseId());
            assertEquals("1.1.0", firstResult.schemaVersion());
            assertTrue(firstResult.genericContent() != null);
            if ("real-three-command".equals(verificationCase.caseId())) {
                assertReviewedGenericSample(firstResult);
            } else {
                assertEquals(1, topUnits(firstResult).size());
                assertEquals(50, nestedUnits(firstResult).size());
            }
        }
    }

    @Test
    void enhancedReleaseMatchesReviewedResultsAcrossThreeRuns() throws Exception {
        var bundle = new ParserReleaseBundleCodec().decode(Path.of("..", "parser-releases", "device-command-output-1.4.0"));
        var plan = new ParserPlanCompiler().compile(bundle);
        var parser = new DefaultDynamicSemanticParser();
        var json = new CanonicalJson();
        for (var verification : bundle.verificationCases()) {
            byte[] expected = verification.expectedResultJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (int run = 0; run < 3; run++) {
                var result = parser.parse(plan, () -> new java.io.ByteArrayInputStream(
                        verification.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                assertArrayEquals(expected, json.bytes(result), verification.caseId());
                assertEquals("1.4.0", result.parserVersion());
                assertEquals(50, nestedUnits(result).size());
                assertEquals(12, nestedUnits(result).stream()
                        .filter(value -> value.structureStatus() == GenericContent.StructureStatus.EMPTY).count());
                assertTable(result, "show environment", 5, 1);
                assertTrue(values(section(unit(result, "show interface"), "recordList"), "records").stream()
                        .map(GenericContent.RecordValue.class::cast).allMatch(record -> !record.sections().isEmpty()));
            }
        }
    }

    @Test
    void legacyRepositoryReleasesRemainByteIdentical() throws Exception {
        for (String version : List.of("1.0.0", "1.0.1")) {
            var bundle = new ParserReleaseBundleCodec().decode(Path.of("..", "parser-releases", "device-command-output-" + version));
            var plan = new ParserPlanCompiler().compile(bundle);
            for (var verification : bundle.verificationCases()) {
                var result = new DefaultDynamicSemanticParser().parse(plan, () -> new java.io.ByteArrayInputStream(
                        verification.inputContent().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                assertArrayEquals(verification.expectedResultJson().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        new CanonicalJson().bytes(result), version + ":" + verification.caseId());
            }
        }
    }

    private static void assertReviewedGenericSample(SemanticParseResult result) {
        assertEquals(3, topUnits(result).size());
        GenericContent.Unit version = unit(result, "show version");
        assertEquals(9, version.sections().stream()
                .filter(section -> Set.of("keyValue", "keyValueTree").contains(section.type()))
                .flatMap(section -> entries(section).stream()).count());
        assertTrue(version.sections().stream().filter(section -> "text".equals(section.type()))
                .flatMap(section -> section.rawLines().stream())
                .anyMatch(line -> line.stripLeading().startsWith("Compiled ")));

        GenericContent.Section running = section(unit(result, "show run"), "configStanza");
        assertEquals(100, values(running, "stanzas").size());
        assertEquals(162, values(running, "stanzas").stream()
                .map(GenericContent.ConfigStanza.class::cast)
                .mapToInt(stanza -> stanza.lines().size()).sum());

        List<GenericContent.Unit> nested = nestedUnits(result);
        assertEquals(50, nested.size());
        assertEquals(13, nested.stream()
                .filter(unit -> unit.structureStatus() == GenericContent.StructureStatus.EMPTY).count());
        assertTable(result, "show device", 4, 7);
        assertTable(result, "show local-group", 3, 5);
        assertEquals(2, values(section(unit(result, "show local-user"), "recordList"), "records").size());
        assertEquals(23, values(section(unit(result, "show interface"), "recordList"), "records").size());
        assertEquals(GenericContent.StructureStatus.EMPTY,
                unit(result, "show session statistic").structureStatus());
        assertEquals(100, values(section(
                unit(result, "show running-config"), "configStanza"), "stanzas").size());
    }

    private static void assertTable(SemanticParseResult result, String command, int columns, int rows) {
        GenericContent.Section table = section(unit(result, command), "table");
        assertEquals(columns, values(table, "columns").size());
        assertEquals(rows, values(table, "rows").size());
    }

    private static List<GenericContent.Unit> topUnits(SemanticParseResult result) {
        return result.genericContent().units().stream()
                .filter(unit -> unit.parentCommandIndex() == null).toList();
    }

    private static List<GenericContent.Unit> nestedUnits(SemanticParseResult result) {
        return result.genericContent().units().stream()
                .filter(unit -> unit.parentCommandIndex() != null).toList();
    }

    private static GenericContent.Unit unit(SemanticParseResult result, String command) {
        return result.genericContent().units().stream()
                .filter(unit -> command.equals(unit.commandText())).findFirst().orElseThrow();
    }

    private static GenericContent.Section section(GenericContent.Unit unit, String type) {
        return unit.sections().stream().filter(section -> type.equals(section.type()))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<GenericContent.KeyValueEntry> entries(GenericContent.Section section) {
        return (List<GenericContent.KeyValueEntry>) section.fields().get("entries");
    }

    private static List<?> values(GenericContent.Section section, String name) {
        return (List<?>) section.fields().get(name);
    }

    private static void assertThreeCommandResult(String caseId, SemanticParseResult result) {
        assertEquals(3, result.observations().size());
        assertTrue(result.observations().stream()
                .allMatch(observation -> observation.status() == ObservationStatus.OBSERVED));
        assertTrue(!map(result.projections().get("deviceBasic")).isEmpty());
        assertTrue(!list(map(result.projections().get("runningConfiguration")).get("stanzas")).isEmpty());
        assertTrue(!list(map(result.projections().get("technicalDiagnostics")).get("sections")).isEmpty());
        if ("real-three-command".equals(caseId)) {
            assertEquals("vpn1000-ga-family", result.profileSelection().profileId());
            assertEquals(51, list(map(result.projections().get("technicalDiagnostics"))
                    .get("sections")).size());
        } else {
            assertEquals("generic", result.profileSelection().profileId());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<?> list(Object value) {
        return (List<?>) value;
    }

    private static Path resource(String name) throws Exception {
        return Path.of(SemanticParserGoldenTest.class.getResource("/fixtures/" + name).toURI());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
