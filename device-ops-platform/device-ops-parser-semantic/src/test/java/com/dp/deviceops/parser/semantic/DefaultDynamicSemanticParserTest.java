package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.input.LineLogInputAdapter;
import com.dp.deviceops.parser.semantic.internal.SemanticLimits;
import com.dp.deviceops.parser.semantic.internal.NestedEvidenceExpander;
import com.dp.deviceops.parser.semantic.internal.NormalizedEvidenceUnit;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.internal.SemanticFact;
import com.dp.deviceops.parser.semantic.plan.ParserExtensionRegistry;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ParserReleaseManifest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DefaultDynamicSemanticParserTest {

    @Test
    void structuredEngineBuildsGenericContentAndReusesItForFacts() {
        ParserPlan plan = new ParserPlanCompiler().compile(structuredBundle());
        DefaultDynamicSemanticParser parser = new DefaultDynamicSemanticParser();
        String json = """
                {"schemaVersion":"1.0.0","collectionId":"structured-1","contextSnapshot":{},
                 "commandBlocks":[
                 {"commandIndex":1,"commandText":"unknown","status":"SUCCEEDED",
                  "stdout":"Name: alpha\\nState: up","stderr":"","pageCount":1,"truncated":false,"exitCode":0},
                 {"commandIndex":2,"commandText":"tree","status":"SUCCEEDED",
                  "stdout":"parent: root\\n    child: leaf","stderr":"","pageCount":1,"truncated":false,"exitCode":0},
                 {"commandIndex":3,"commandText":"config","status":"SUCCEEDED",
                  "stdout":"interface eth0\\n address 10.0.0.1\\n!\\ninterface eth1\\n shutdown","stderr":"","pageCount":1,"truncated":false,"exitCode":0},
                 {"commandIndex":4,"commandText":"records","status":"SUCCEEDED",
                  "stdout":"Name: one\\nState: up\\nName: two\\nState: down","stderr":"","pageCount":1,"truncated":false,"exitCode":0},
                 {"commandIndex":5,"commandText":"items","status":"SUCCEEDED",
                  "stdout":"- first\\n- second","stderr":"","pageCount":1,"truncated":false,"exitCode":0},
                 {"commandIndex":6,"commandText":"section","status":"SUCCEEDED",
                  "stdout":"Code: value","stderr":"","pageCount":1,"truncated":false,"exitCode":0}]}
                """;

        SemanticParseResult result = parser.parse(plan, () -> input(json));

        assertEquals("1.1.0", result.schemaVersion());
        assertNotNull(result.genericContent());
        assertEquals(ObservationStatus.UNPARSED, result.observations().getFirst().status());
        assertEquals(GenericContent.StructureStatus.STRUCTURED,
                result.genericContent().units().getFirst().structureStatus());
        Map<String, Object> entities = map(result.snapshot().get("entities"));
        assertEquals("leaf", map(map(entities.get("tree")).get("child")).get("value"));
        assertEquals(2, list(map(entities.get("config")).get("stanzas")).size());
        assertEquals(2, list(map(entities.get("diagnostics")).get("records")).size());
        assertEquals(List.of("first", "second"), list(map(entities.get("diagnostics")).get("items"))
                .stream().map(DefaultDynamicSemanticParserTest::map)
                .map(fact -> fact.get("value")).toList());
        assertEquals(1, list(map(entities.get("diagnostics")).get("sections")).size());

        CanonicalJson canonical = new CanonicalJson();
        byte[] expected = canonical.bytes(result);
        assertArrayEquals(expected, canonical.bytes(parser.parse(plan, () -> input(json))));
        assertArrayEquals(expected, canonical.bytes(parser.parse(plan, () -> input(json))));
    }

    @Test
    void structuredEngineRedactsEvidenceWithoutChangingInputIdentity() {
        ParserPlan plan = new ParserPlanCompiler().compile(structuredBundle());
        DefaultDynamicSemanticParser parser = new DefaultDynamicSemanticParser();
        String firstToken = "DUMMY_SECRET_ALPHA";
        String secondToken = "DUMMY_SECRET_BETA";

        SemanticParseResult first = parser.parse(plan,
                () -> input(sensitiveInput(firstToken)));
        SemanticParseResult second = parser.parse(plan,
                () -> input(sensitiveInput(secondToken)));
        String serialized = new String(new CanonicalJson().bytes(first), StandardCharsets.UTF_8);

        assertFalse(serialized.contains(firstToken));
        assertTrue(serialized.contains("[REDACTED]"));
        assertNotEquals(first.inputSha256(), second.inputSha256());
        assertTrue(((Number) first.quality().get("redactedValueCount")).intValue() > 0);
    }

    @Test
    void executesTwoExplicitPlansWithoutResolvingALatestAlias() {
        ParserPlanCompiler compiler = new ParserPlanCompiler(SemanticLimits.defaults(),
                List.of(new LineLogInputAdapter()), new ParserExtensionRegistry(List.of()));
        var firstPlan = compiler.compile(coordinate("1.0.0", "1.0.0"), "line-log/v1",
                ParserPlanCompilerTest.specification("1.0.0", "release: (\\S+)"));
        var secondPlan = compiler.compile(coordinate("2.0.0", "2.0.0"), "line-log/v1",
                ParserPlanCompilerTest.specification("2.0.0", "release: (\\S+)"));
        DefaultDynamicSemanticParser parser = new DefaultDynamicSemanticParser();

        SemanticParseResult first = parser.parse(firstPlan, () -> input("release: STABLE"));
        SemanticParseResult second = parser.parse(secondPlan, () -> input("release: STABLE"));

        assertEquals("1.0.0", first.ruleVersion());
        assertEquals("2.0.0", second.ruleVersion());
        assertEquals(first.projections(), second.projections());
    }

    @Test
    void selectsModelProfileFromLogThenContextThenGeneric() {
        ParserPlanCompiler compiler = new ParserPlanCompiler();
        var plan = compiler.compile(modelAwareBundle());
        DefaultDynamicSemanticParser parser = new DefaultDynamicSemanticParser();

        SemanticParseResult log = parse(parser, plan, "Model: VPN1000-GA-X", "VPN1000-GA-X", List.of());
        assertEquals("vpn-family", log.profileSelection().profileId());
        assertEquals(ModelProfileSelection.Source.LOG_OUTPUT, log.profileSelection().source());
        assertEquals(1, log.profileSelection().commandIndex());

        SemanticParseResult conflict = parse(parser, plan, "Model: VPN1000-GA-X", "OTHER-1", List.of());
        assertEquals("vpn-family", conflict.profileSelection().profileId());
        assertEquals(List.of("MODEL_HINT_CONFLICT"), conflict.profileSelection().warnings());

        SemanticParseResult context = parse(parser, plan, "Software: 1.0", "VPN1000-GA-Y", List.of());
        assertEquals("vpn-family", context.profileSelection().profileId());
        assertEquals(ModelProfileSelection.Source.CONTEXT_SNAPSHOT, context.profileSelection().source());

        SemanticParseResult generic = parse(parser, plan, "Software: 1.0", null, List.of());
        assertEquals("generic", generic.profileSelection().profileId());
        assertEquals(ModelProfileSelection.Source.GENERIC, generic.profileSelection().source());
        assertNotEquals(context.inputSha256(), generic.inputSha256());
    }

    @Test
    void compatibilityWarningsDoNotAffectSemanticSelectionOrProjection() {
        var plan = new ParserPlanCompiler().compile(modelAwareBundle());
        DefaultDynamicSemanticParser parser = new DefaultDynamicSemanticParser();

        SemanticParseResult clean = parse(parser, plan, "Model: VPN1000-GA-X", null, List.of());
        SemanticParseResult noisy = parse(parser, plan, "Model: VPN1000-GA-X", null,
                List.of("missing delimiter", "another compatibility warning"));

        assertEquals(clean.profileSelection(), noisy.profileSelection());
        assertEquals(clean.projections(), noisy.projections());
        assertEquals(clean.inputSha256(), noisy.inputSha256());
    }

    @Test
    void expandsCapturedSectionsWithoutAssumingVendorCommands() {
        ParserPlan plan = new ParserPlanCompiler(SemanticLimits.defaults(),
                List.of(new LineLogInputAdapter()), new ParserExtensionRegistry(List.of()))
                .compile(new ParserCoordinate("system.runtime-log", "1.0.0",
                                ParserPlanCompiler.NESTED_ENGINE_VERSION, "1.0.0", "1.0.0", null, null),
                        "line-log/v1",
                        nestedSectionSpecification());
        NormalizedEvidenceUnit aggregate = new NormalizedEvidenceUnit(3, "COMMAND_OUTPUT",
                Map.of("commandText", "collect all"), List.of(
                        "collect all",
                        "--- alpha inspect ---",
                        "Model: A-1",
                        "--- mystery query ---",
                        "opaque result"), List.of(), false);
        BlockObservation observation = new BlockObservation(3, "AGGREGATE",
                ObservationStatus.OBSERVED, 0.85, 1, 5, List.of("aggregate-sections"), List.of());

        NestedEvidenceExpander.Expansion expansion = new NestedEvidenceExpander(SemanticLimits.defaults())
                .expand(List.of(aggregate), List.of(observation), plan.rules());

        assertEquals(List.of("alpha inspect", "mystery query"), expansion.units().stream()
                .map(NormalizedEvidenceUnit::commandText).toList());
        assertEquals(List.of(2, 4), expansion.identities().values().stream()
                .map(NestedEvidenceExpander.NestedIdentity::lineStart).toList());
        assertEquals(List.of(3, 5), expansion.identities().values().stream()
                .map(NestedEvidenceExpander.NestedIdentity::lineEnd).toList());
        assertTrue(expansion.units().get(1).contentLines().contains("opaque result"));
    }

    @Test
    void mergesNestedFactsAfterDirectEvidenceAndReportsUnknownSections() {
        var plan = new ParserPlanCompiler().compile(nestedSemanticBundle());
        DefaultDynamicSemanticParser parser = new DefaultDynamicSemanticParser();
        String aggregateOnly = """
                {"schemaVersion":"1.0.0","collectionId":"nested-1","contextSnapshot":{},
                 "commandBlocks":[{"commandIndex":1,"commandText":"bundle","status":"SUCCEEDED",
                 "stdout":"--- version detail ---\\nModel: VPN-X\\nSerial: SN-9\\nSoftware: NESTED\\n--- configuration detail ---\\ninterface eth0\\n address 10.0.0.1\\n--- mystery query ---\\nopaque result",
                 "stderr":"","pageCount":1,"truncated":false,"exitCode":0}]}
                """;

        SemanticParseResult nested = parser.parse(plan, () -> input(aggregateOnly));

        Map<String, Object> basic = map(nested.projections().get("deviceBasic"));
        assertEquals("VPN-X", basic.get("model"));
        assertEquals("SN-9", basic.get("sn"));
        assertEquals("NESTED", basic.get("softVersion"));
        assertEquals(3, list(map(nested.projections().get("technicalDiagnostics"))
                .get("sections")).size());
        assertEquals(3, nested.nestedObservations().size());
        assertEquals(ObservationStatus.UNPARSED,
                nested.nestedObservations().get(2).status());
        assertEquals(3, nested.quality().get("nestedBlockCount"));
        assertEquals(2L, nested.quality().get("nestedMappedBlockCount"));
        assertEquals(1L, nested.quality().get("nestedUnparsedBlockCount"));

        String directAndAggregate = """
                {"schemaVersion":"1.0.0","collectionId":"nested-2","contextSnapshot":{},
                 "commandBlocks":[
                 {"commandIndex":1,"commandText":"version detail","status":"SUCCEEDED",
                 "stdout":"Model: VPN-X\\nSerial: SN-9\\nSoftware: DIRECT","stderr":"",
                 "pageCount":1,"truncated":false,"exitCode":0},
                 {"commandIndex":2,"commandText":"bundle","status":"SUCCEEDED",
                 "stdout":"--- version detail ---\\nModel: VPN-X\\nSerial: SN-9\\nSoftware: NESTED\\n--- mystery query ---\\nopaque result",
                 "stderr":"","pageCount":1,"truncated":false,"exitCode":0}]}
                """;
        SemanticParseResult merged = parser.parse(plan, () -> input(directAndAggregate));
        assertEquals("DIRECT", map(merged.projections().get("deviceBasic")).get("softVersion"));
        List<?> conflicts = list(map(merged.snapshot().get("quality")).get("conflicts"));
        assertTrue(conflicts.stream().map(DefaultDynamicSemanticParserTest::map)
                .map(conflict -> (SemanticFact.SourceEvidence) conflict.get("discardedSource"))
                .anyMatch(source -> Integer.valueOf(1).equals(source.nestingDepth())));

        CanonicalJson json = new CanonicalJson();
        byte[] first = json.bytes(parser.parse(plan, () -> input(aggregateOnly)));
        assertArrayEquals(first, json.bytes(parser.parse(plan, () -> input(aggregateOnly))));
        assertArrayEquals(first, json.bytes(parser.parse(plan, () -> input(aggregateOnly))));
    }

    private static SemanticParseResult parse(DefaultDynamicSemanticParser parser,
            com.dp.deviceops.parser.semantic.plan.ParserPlan plan, String stdout,
            String contextModel, List<String> warnings) {
        String context = contextModel == null ? "{}" : "{\"deviceModel\":\"" + contextModel + "\"}";
        String warningJson = warnings.stream().map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        String json = """
                {"schemaVersion":"1.0.0","collectionId":"c-1","contextSnapshot":%s,
                 "commandBlocks":[{"commandIndex":1,"commandText":"show version","status":"SUCCEEDED",
                 "stdout":"%s","stderr":"","pageCount":1,"truncated":false,"exitCode":0,
                 "parsedFacts":{"ignored":"value"},"parseWarnings":%s}]}
                """.formatted(context, stdout, warningJson);
        return parser.parse(plan, () -> input(json));
    }

    private static ParserReleaseBundle modelAwareBundle() {
        ParserReleaseManifest manifest = new ParserReleaseManifest("1.0.0", "device-command-output",
                "1.1.0", "command-output-block/v1", "1.0.0", "1.0.0", "1.1.0",
                "1.1.0", "1.0.0", null);
        String profiles = """
                {"schemaVersion":"1.0.0","baseRuleSets":["rules/base.json"],
                 "genericProfile":{"profileId":"generic","ruleSets":[]},
                 "profiles":[{"profileId":"vpn-family","match":{"exact":[],"prefixes":["VPN1000-"]},
                 "ruleSets":["rules/vpn.json"]}]}
                """;
        String baseRules = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.1.0","rules":[{
                 "ruleId":"model","blockRole":"DEVICE_VERSION",
                 "roleSelectors":[{"type":"COMMAND_REGEX","value":"^show version$"}],
                 "extractors":[{"type":"LINE_REGEX","pattern":"Model: (.+)","group":1,"transforms":["trim"]}],
                 "target":{"semanticKey":"device.identity.model","cardinality":"ONE","dataType":"string",
                 "conflictPolicy":"HIGHEST_CONFIDENCE"}}]}
                """;
        String emptyRules = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.1.0","rules":[]}
                """;
        String projections = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.0.0","profiles":[{
                 "projectionId":"deviceBasic","missingValuePolicy":"OMIT",
                 "fields":{"model":"device.identity.model"}}]}
                """;
        return new ParserReleaseBundle(manifest, null, projections, profiles,
                Map.of("rules/base.json", baseRules, "rules/vpn.json", emptyRules),
                List.of(new ParserReleaseBundle.VerificationCase("placeholder", "{}", "{}")));
    }

    private static ParserReleaseBundle structuredBundle() {
        ParserReleaseManifest manifest = new ParserReleaseManifest("1.0.0", "device-command-output",
                "1.3.0", "command-output-block/v1", "1.0.0", "1.0.0", "1.3.0",
                "1.3.0", ParserPlanCompiler.STRUCTURED_ENGINE_VERSION, null);
        String profiles = """
                {"schemaVersion":"1.0.0","baseRuleSets":["rules/base.json"],
                 "genericProfile":{"profileId":"generic","ruleSets":[]},"profiles":[]}
                """;
        String rules = """
                {"schemaVersion":"1.1.0","catalogVersion":"1.3.0",
                 "structureRules":[
                 {"structureRuleId":"records-shape","selectors":[{"type":"COMMAND_ALIAS","value":"records"}],
                  "mode":"FORCE","type":"recordList","options":{}},
                 {"structureRuleId":"items-shape","selectors":[{"type":"COMMAND_ALIAS","value":"items"}],
                 "mode":"FORCE","type":"list","options":{}}],
                 "rules":[
                 {"ruleId":"model-discovery","blockRole":"TREE","roleSelectors":[{"type":"COMMAND_ALIAS","value":"tree"}],
                  "extractors":[{"type":"KEY_VALUE","key":"parent"}],
                  "target":{"semanticKey":"device.identity.model","cardinality":"ONE","dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}},
                 {"ruleId":"tree-child","blockRole":"TREE","roleSelectors":[{"type":"COMMAND_ALIAS","value":"tree"}],
                  "extractors":[{"type":"KEY_VALUE","key":"child"}],
                  "target":{"semanticKey":"tree.child","cardinality":"ONE","dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}},
                 {"ruleId":"config-stanzas","blockRole":"CONFIG","roleSelectors":[{"type":"COMMAND_ALIAS","value":"config"}],
                  "extractors":[{"type":"CONFIG_STANZA"}],
                  "target":{"semanticKey":"config.stanzas","cardinality":"MANY","dataType":"object","conflictPolicy":"APPEND_DISTINCT"}},
                 {"ruleId":"records","blockRole":"RECORDS","roleSelectors":[{"type":"COMMAND_ALIAS","value":"records"}],
                  "extractors":[{"type":"RECORD_LIST"}],
                  "target":{"semanticKey":"diagnostics.records","cardinality":"MANY","dataType":"object","conflictPolicy":"APPEND_DISTINCT"}},
                 {"ruleId":"items","blockRole":"ITEMS","roleSelectors":[{"type":"COMMAND_ALIAS","value":"items"}],
                  "extractors":[{"type":"LIST"}],
                  "target":{"semanticKey":"diagnostics.items","cardinality":"MANY","dataType":"string","conflictPolicy":"APPEND_DISTINCT"}},
                 {"ruleId":"section","blockRole":"SECTION","roleSelectors":[{"type":"COMMAND_ALIAS","value":"section"}],
                  "extractors":[{"type":"STRUCTURE_SECTION"}],
                  "target":{"semanticKey":"diagnostics.sections","cardinality":"MANY","dataType":"object","conflictPolicy":"APPEND_DISTINCT"}}]}
                """;
        String projections = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.3.0","profiles":[]}
                """;
        return new ParserReleaseBundle(manifest, null, projections, profiles,
                Map.of("rules/base.json", rules),
                List.of(new ParserReleaseBundle.VerificationCase("placeholder", "{}", "{}")));
    }

    private static String sensitiveInput(String token) {
        return """
                {"schemaVersion":"1.0.0","collectionId":"sensitive","contextSnapshot":{},
                 "commandBlocks":[{"commandIndex":1,"commandText":"unknown","status":"SUCCEEDED",
                 "stdout":"password encrypted-password %s\\ntail text","stderr":"","pageCount":1,
                 "truncated":false,"exitCode":0}]}
                """.formatted(token);
    }

    private static SemanticParserSpecification nestedSectionSpecification() {
        String rules = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.0.0","rules":[{
                 "ruleId":"aggregate-sections","blockRole":"AGGREGATE",
                 "roleSelectors":[{"type":"COMMAND_REGEX","value":"^collect all$"}],
                 "extractors":[{"type":"DELIMITED_SECTION",
                 "startPattern":"^---\\\\s+(?<command>.+?)\\\\s+---$","headerGroup":"command",
                 "emitNestedUnits":true}],
                 "target":{"semanticKey":"diagnostics.sections","cardinality":"MANY",
                 "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}}]}
                """;
        String projections = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.0.0","profiles":[{
                 "projectionId":"diagnostics","missingValuePolicy":"OMIT",
                 "fields":{"sections":"diagnostics.sections"}}]}
                """;
        return new SemanticParserSpecification(rules, projections);
    }

    private static ParserReleaseBundle nestedSemanticBundle() {
        ParserReleaseManifest manifest = new ParserReleaseManifest("1.0.0", "device-command-output",
                "1.2.0", "command-output-block/v1", "1.0.0", "1.0.0", "1.2.0",
                "1.2.0", "1.2.0", null);
        String profiles = """
                {"schemaVersion":"1.0.0","baseRuleSets":["rules/base.json"],
                 "genericProfile":{"profileId":"generic","ruleSets":[]},
                 "profiles":[{"profileId":"vpn-family","match":{"exact":[],"prefixes":["VPN-"]},
                 "ruleSets":["rules/vpn.json"]}]}
                """;
        String baseRules = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.2.0","rules":[
                 {"ruleId":"model-discovery","blockRole":"DEVICE_VERSION",
                 "roleSelectors":[{"type":"CONTENT_REGEX","value":"Model:\\\\s*VPN-"}],
                 "extractors":[{"type":"LINE_REGEX","pattern":"Model:\\\\s*(\\\\S+)","group":1,
                 "transforms":["trim"]}],
                 "target":{"semanticKey":"device.identity.model","cardinality":"ONE",
                 "dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}},
                 {"ruleId":"aggregate-sections","blockRole":"TECHNICAL_DIAGNOSTICS",
                 "roleSelectors":[{"type":"COMMAND_REGEX","value":"^bundle$"}],
                 "extractors":[{"type":"DELIMITED_SECTION",
                 "startPattern":"^---\\\\s+(?<command>.+?)\\\\s+---$","headerGroup":"command",
                 "emitNestedUnits":true}],
                 "target":{"semanticKey":"diagnostics.sections","cardinality":"MANY",
                 "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}}]}
                """;
        String profileRules = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.2.0","rules":[
                 {"ruleId":"version-model","blockRole":"DEVICE_VERSION",
                 "roleSelectors":[{"type":"COMMAND_REGEX","value":"^version detail$"}],
                 "extractors":[{"type":"LINE_REGEX","pattern":"Model:\\\\s*(\\\\S+)","group":1,
                 "transforms":["trim"]}],"target":{"semanticKey":"device.identity.model",
                 "cardinality":"ONE","dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}},
                 {"ruleId":"version-serial","blockRole":"DEVICE_VERSION",
                 "roleSelectors":[{"type":"COMMAND_REGEX","value":"^version detail$"}],
                 "extractors":[{"type":"LINE_REGEX","pattern":"Serial:\\\\s*(\\\\S+)","group":1,
                 "transforms":["trim"]}],"target":{"semanticKey":"device.identity.serialNumber",
                 "cardinality":"ONE","dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}},
                 {"ruleId":"version-software","blockRole":"DEVICE_VERSION",
                 "roleSelectors":[{"type":"COMMAND_REGEX","value":"^version detail$"}],
                 "extractors":[{"type":"LINE_REGEX","pattern":"Software:\\\\s*(\\\\S+)","group":1,
                 "transforms":["trim"]}],"target":{"semanticKey":"device.software.version",
                 "cardinality":"ONE","dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}},
                 {"ruleId":"configuration","blockRole":"RUNNING_CONFIGURATION",
                 "roleSelectors":[{"type":"COMMAND_REGEX","value":"^configuration detail$"}],
                 "extractors":[{"type":"CONFIG_STANZA"}],
                 "target":{"semanticKey":"configuration.stanzas","cardinality":"MANY",
                 "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}}]}
                """;
        String projections = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.2.0","profiles":[
                 {"projectionId":"deviceBasic","missingValuePolicy":"OMIT","fields":{
                 "model":"device.identity.model","sn":"device.identity.serialNumber",
                 "softVersion":"device.software.version"}},
                 {"projectionId":"runningConfiguration","missingValuePolicy":"OMIT",
                 "fields":{"stanzas":"configuration.stanzas"}},
                 {"projectionId":"technicalDiagnostics","missingValuePolicy":"OMIT",
                 "fields":{"sections":"diagnostics.sections"}}]}
                """;
        return new ParserReleaseBundle(manifest, null, projections, profiles,
                Map.of("rules/base.json", baseRules, "rules/vpn.json", profileRules),
                List.of(new ParserReleaseBundle.VerificationCase("placeholder", "{}", "{}")));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static List<?> list(Object value) {
        return (List<?>) value;
    }

    private static ParserCoordinate coordinate(String releaseVersion, String ruleVersion) {
        return new ParserCoordinate("system.runtime-log", releaseVersion, ParserPlanCompiler.ENGINE_VERSION,
                ruleVersion, "1.0.0", null, null);
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
