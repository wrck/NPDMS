package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.input.LineLogInputAdapter;
import com.dp.deviceops.parser.semantic.internal.SemanticLimits;
import com.dp.deviceops.parser.semantic.plan.ParserExtension;
import com.dp.deviceops.parser.semantic.plan.ParserExtensionRegistry;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ParserReleaseManifest;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserPlanCompilerTest {

    @Test
    void structuredEngineCompilesStructureRulesAndRetainsNestedUnits() {
        ParserPlanCompiler compiler = compiler(List.of());
        var plan = compiler.compile(coordinate("1.3.0", "1.0.0", null, null), "line-log/v1",
                structuredNestedSpecification());

        assertEquals(2, plan.structureRules().size());
        assertEquals("records", plan.structureRules().getFirst().source().structureRuleId());
        assertNotNull(plan.structureRules().getFirst().recordStartPattern());
        assertEquals(List.of("!"), plan.structureRules().get(1).configSeparators());
        assertTrue(plan.rules().getFirst().extractors().getFirst().source().emitNestedUnits());
    }

    @Test
    void enhancedEngineCompilesExactCoordinateWithStructureRulesAndNestedUnits() {
        ParserCoordinate coordinate = coordinate("1.4.0", "1.0.0", null, null);

        var plan = assertDoesNotThrow(() -> compiler(List.of()).compile(coordinate, "line-log/v1",
                structuredNestedSpecification()));

        assertEquals(coordinate, plan.coordinate());
        assertEquals("1.1.0", plan.catalogs().ruleCatalog().schemaVersion());
        assertEquals(2, plan.structureRules().size());
        assertNotNull(plan.structureRules().getFirst().recordStartPattern());
        assertEquals(List.of("!"), plan.structureRules().get(1).configSeparators());
        assertTrue(plan.rules().getFirst().extractors().getFirst().source().emitNestedUnits());
    }

    @Test
    void enhancedEngineCompilesModelAwareBundleWithStructureRulesAndNestedUnits() {
        LinkedHashMap<String, String> ruleSets = new LinkedHashMap<>();
        ruleSets.put("rules/base.json", modelAwareRules("discovery", "base-structure"));
        ruleSets.put("rules/profile.json", structuredNestedSpecification().ruleCatalogJson());

        var plan = assertDoesNotThrow(() -> compiler(List.of()).compile(modelAwareBundle("1.4.0", ruleSets)));

        assertEquals("1.4.0", plan.coordinate().engineVersion());
        assertEquals("1.1.0", plan.catalogs().ruleCatalog().schemaVersion());
        assertEquals(3, plan.catalogs().ruleCatalog().structureRules().size());
        assertEquals("discovery", plan.discoveryRules().getFirst().source().ruleId());
        assertEquals("base-structure", plan.genericProfile().structureRules().getFirst().source().structureRuleId());
        var profile = plan.modelProfiles().getFirst();
        assertEquals("profile", profile.profileId());
        assertEquals(2, profile.structureRules().size());
        assertTrue(profile.rules().getFirst().extractors().getFirst().source().emitNestedUnits());
    }

    @Test
    void olderEngineRejectsStructureRules() {
        SemanticParserError error = assertThrows(SemanticParserError.class,
                () -> compiler(List.of()).compile(coordinate(ParserPlanCompiler.NESTED_ENGINE_VERSION,
                                "1.0.0", null, null), "line-log/v1",
                        structuredNestedSpecification()));
        assertEquals(SemanticParserError.INVALID_RULES, error.code());
    }

    @Test
    void rejectsDuplicateStructureRuleIdsAcrossRuleSets() {
        LinkedHashMap<String, String> ruleSets = new LinkedHashMap<>();
        ruleSets.put("rules/base.json", modelAwareRules("base-rule", "same"));
        ruleSets.put("rules/profile.json", modelAwareRules("profile-rule", "same"));

        SemanticParserError error = assertThrows(SemanticParserError.class,
                () -> compiler(List.of()).compile(modelAwareBundle(ruleSets)));
        assertEquals(SemanticParserError.INVALID_RULES, error.code());
    }

    @Test
    void compilesRegexOnceForAnExactCoordinate() {
        ParserPlanCompiler compiler = compiler(List.of());
        ParserCoordinate coordinate = coordinate("1.0.0", null, null);

        var plan = compiler.compile(coordinate, "line-log/v1", specification("1.0.0", "release: (\\S+)"));

        assertEquals(coordinate, plan.coordinate());
        assertNotNull(plan.rules().getFirst().selectors().getFirst().pattern());
        assertNotNull(plan.rules().getFirst().extractors().getFirst().pattern());
    }

    @Test
    void rejectsUnavailableAdapterExtensionAndCoordinateMismatch() {
        ParserPlanCompiler compiler = compiler(List.of());

        assertEquals(SemanticParserError.INVALID_RULES, assertThrows(SemanticParserError.class,
                () -> compiler.compile(coordinate("1.0.0", null, null), "missing/v1",
                        specification("1.0.0", "release: (\\S+)"))).code());
        assertEquals(SemanticParserError.INVALID_RULES, assertThrows(SemanticParserError.class,
                () -> compiler.compile(coordinate("1.0.0", "advanced", "2.0.0"), "line-log/v1",
                        specification("1.0.0", "release: (\\S+)"))).code());
        assertEquals(SemanticParserError.INVALID_RULES, assertThrows(SemanticParserError.class,
                () -> compiler.compile(coordinate("2.0.0", null, null), "line-log/v1",
                        specification("1.0.0", "release: (\\S+)"))).code());
    }

    @Test
    void rejectsDuplicateExtensionsAndInvalidRegex() {
        ParserExtension extension = new StubExtension();
        assertEquals(SemanticParserError.INVALID_RULES, assertThrows(SemanticParserError.class,
                () -> new ParserExtensionRegistry(List.of(extension, extension))).code());
        assertEquals(SemanticParserError.INVALID_RULES, assertThrows(SemanticParserError.class,
                () -> compiler(List.of()).compile(coordinate("1.0.0", null, null), "line-log/v1",
                        specification("1.0.0", "["))).code());
    }

    @Test
    void validatesNestedDelimitedSectionDeclaration() {
        ParserPlanCompiler compiler = compiler(List.of());

        assertNotNull(compiler.compile(coordinate(ParserPlanCompiler.NESTED_ENGINE_VERSION,
                        "1.0.0", null, null), "line-log/v1",
                nestedSpecification("DELIMITED_SECTION", "(?<command>[^:]+):", "command")));
        assertEquals(SemanticParserError.INVALID_RULES, assertThrows(SemanticParserError.class,
                () -> compiler.compile(coordinate(ParserPlanCompiler.NESTED_ENGINE_VERSION,
                                "1.0.0", null, null), "line-log/v1",
                        nestedSpecification("LINE_REGEX", "(?<command>[^:]+):", "command"))).code());
        assertEquals(SemanticParserError.INVALID_RULES, assertThrows(SemanticParserError.class,
                () -> compiler.compile(coordinate(ParserPlanCompiler.NESTED_ENGINE_VERSION,
                                "1.0.0", null, null), "line-log/v1",
                        nestedSpecification("DELIMITED_SECTION", "(?<header>[^:]+):", "command"))).code());
        assertEquals(SemanticParserError.INVALID_RULES, assertThrows(SemanticParserError.class,
                () -> compiler.compile(coordinate("1.0.0", null, null), "line-log/v1",
                        nestedSpecification("DELIMITED_SECTION", "(?<command>[^:]+):", "command"))).code());
    }

    private static ParserPlanCompiler compiler(List<ParserExtension> extensions) {
        return new ParserPlanCompiler(SemanticLimits.defaults(), List.of(new LineLogInputAdapter()),
                new ParserExtensionRegistry(extensions));
    }

    private static ParserCoordinate coordinate(
            String ruleVersion,
            String extensionId,
            String extensionVersion) {
        return coordinate(ParserPlanCompiler.ENGINE_VERSION, ruleVersion, extensionId, extensionVersion);
    }

    private static ParserCoordinate coordinate(
            String engineVersion,
            String ruleVersion,
            String extensionId,
            String extensionVersion) {
        return new ParserCoordinate("system.runtime-log", "1.0.0", engineVersion,
                ruleVersion, "1.0.0", extensionId, extensionVersion);
    }

    static SemanticParserSpecification specification(String ruleVersion, String pattern) {
        String rules = """
                {"schemaVersion":"1.0.0","catalogVersion":"%s","rules":[{
                  "ruleId":"release","blockRole":"RELEASE",
                  "roleSelectors":[{"type":"CONTENT_REGEX","value":"release:"}],
                  "extractors":[{"type":"LINE_REGEX","pattern":"%s","group":1,"transforms":["trim"]}],
                  "target":{"semanticKey":"logs.release","cardinality":"ONE","dataType":"string",
                            "conflictPolicy":"HIGHEST_CONFIDENCE"}
                }]}
                """.formatted(ruleVersion, pattern.replace("\\", "\\\\"));
        String projections = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.0.0","profiles":[{
                  "projectionId":"logInfo","missingValuePolicy":"NULL","fields":{"release":"logs.release"}
                }]}
                """;
        return new SemanticParserSpecification(rules, projections);
    }

    private static SemanticParserSpecification nestedSpecification(
            String extractorType,
            String startPattern,
            String headerGroup) {
        String rules = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.0.0","rules":[{
                  "ruleId":"sections","blockRole":"AGGREGATE",
                  "roleSelectors":[{"type":"CONTENT_REGEX","value":"section:"}],
                  "extractors":[{"type":"%s","startPattern":"%s","headerGroup":"%s",
                                 "emitNestedUnits":true}],
                  "target":{"semanticKey":"diagnostics.sections","cardinality":"MANY",
                            "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}
                }]}
                """.formatted(extractorType, startPattern.replace("\\", "\\\\"), headerGroup);
        String projections = """
                {"schemaVersion":"1.0.0","catalogVersion":"1.0.0","profiles":[{
                  "projectionId":"diagnostics","missingValuePolicy":"OMIT",
                  "fields":{"sections":"diagnostics.sections"}
                }]}
                """;
        return new SemanticParserSpecification(rules, projections);
    }

    private static SemanticParserSpecification structuredNestedSpecification() {
        String rules = """
                {"schemaVersion":"1.1.0","catalogVersion":"1.0.0",
                 "structureRules":[
                   {"structureRuleId":"records","selectors":[{"type":"COMMAND_ALIAS","value":"show aggregate"}],
                    "mode":"FORCE","type":"recordList",
                    "options":{"recordStartPattern":"^ID: (?<id>\\\\S+)$"}},
                   {"structureRuleId":"config","selectors":[{"type":"COMMAND_ALIAS","value":"show config"}],
                    "mode":"FORCE","type":"configStanza"}],
                 "rules":[{
                  "ruleId":"sections","blockRole":"AGGREGATE",
                  "roleSelectors":[{"type":"CONTENT_REGEX","value":"section:"}],
                  "extractors":[{"type":"DELIMITED_SECTION","startPattern":"(?<command>[^:]+):",
                                 "headerGroup":"command","emitNestedUnits":true}],
                  "target":{"semanticKey":"diagnostics.sections","cardinality":"MANY",
                            "dataType":"object","conflictPolicy":"APPEND_DISTINCT"}
                }]}
                """;
        return new SemanticParserSpecification(rules,
                "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"1.0.0\",\"profiles\":[]}");
    }

    private static String modelAwareRules(String ruleId, String structureRuleId) {
        return """
                {"schemaVersion":"1.1.0","catalogVersion":"1.0.0",
                 "structureRules":[{"structureRuleId":"%s",
                   "selectors":[{"type":"COMMAND_ALIAS","value":"show version"}],"mode":"AUTO"}],
                 "rules":[{"ruleId":"%s","blockRole":"IDENTITY",
                   "roleSelectors":[{"type":"COMMAND_ALIAS","value":"show version"}],
                   "extractors":[{"type":"LINE_REGEX","pattern":"model: (\\\\S+)","group":1}],
                   "target":{"semanticKey":"device.identity.model","cardinality":"ONE",
                     "dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}}]}
                """.formatted(structureRuleId, ruleId);
    }

    private static ParserReleaseBundle modelAwareBundle(Map<String, String> ruleSets) {
        return modelAwareBundle("1.3.0", ruleSets);
    }

    private static ParserReleaseBundle modelAwareBundle(String engineVersion, Map<String, String> ruleSets) {
        ParserReleaseManifest manifest = new ParserReleaseManifest("1.0.0", "system.runtime-log",
                "1.0.0", "line-log/v1", "1.0.0", "1.1.0", engineVersion, "1.0.0", "1.0.0", null);
        String projections = "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"1.0.0\",\"profiles\":[]}";
        String profiles = """
                {"schemaVersion":"1.0.0",
                 "baseRuleSets":["rules/base.json"],
                 "genericProfile":{"profileId":"generic","match":{"exact":[],"prefixes":[]},
                   "ruleSets":["rules/base.json"]},
                 "profiles":[{"profileId":"profile","match":{"exact":["x"],"prefixes":[]},
                   "ruleSets":["rules/profile.json"]}]}
                """;
        return new ParserReleaseBundle(manifest, null, projections, profiles, ruleSets,
                List.of(new ParserReleaseBundle.VerificationCase("case", "input", "{}")));
    }

    private static final class StubExtension implements ParserExtension {
        @Override public String extensionId() { return "advanced"; }
        @Override public String extensionVersion() { return "2.0.0"; }
        @Override public ExtensionResult extract(EvidenceDocument document, ExtensionContext context) {
            return ExtensionResult.empty();
        }
    }
}
