package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalogCodec;
import com.dp.deviceops.parser.semantic.internal.SemanticLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SemanticCatalogCodecTest {

    @Test
    void acceptsLegacyCatalogWithoutStructureRulesAndValidForcedRecordList() {
        assertNotNull(codec().decode(new SemanticParserSpecification(
                "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"test\",\"rules\":[]}",
                emptyProjections())));

        String rules = structureRules("""
                {"structureRuleId":"records","selectors":[{"type":"COMMAND_ALIAS","value":"show records"}],
                 "mode":"FORCE","type":"recordList",
                 "options":{"recordStartPattern":"^ID: (?<id>\\\\S+)$"}}
                """);
        assertNotNull(codec().decode(new SemanticParserSpecification(rules, emptyProjections())));
    }

    @Test
    void rejectsStructureRulesDeclaredWithLegacySchema() {
        String rules = structureRules("""
                {"structureRuleId":"auto","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"AUTO"}
                """).replace("\"schemaVersion\":\"1.1.0\"", "\"schemaVersion\":\"1.0.0\"");
        SemanticParserError error = assertThrows(SemanticParserError.class,
                () -> codec().decode(new SemanticParserSpecification(rules, emptyProjections())));
        assertEquals(SemanticParserError.INVALID_RULES, error.code());
    }

    @Test
    void rejectsInvalidStructureModesTypesAndOptions() {
        assertInvalidStructure("""
                {"structureRuleId":"auto","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"AUTO","type":"table"}
                """);
        assertInvalidStructure("""
                {"structureRuleId":"text","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"TEXT","options":{"columnNames":["name"]}}
                """);
        assertInvalidStructure("""
                {"structureRuleId":"force","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"FORCE"}
                """);
        assertInvalidStructure("""
                {"structureRuleId":"mode","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"GUESS"}
                """);
        assertInvalidStructure("""
                {"structureRuleId":"type","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"FORCE","type":"xml"}
                """);
        assertInvalidStructure("""
                {"structureRuleId":"option","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"FORCE","type":"list","options":{"columnNames":["name"]}}
                """);
    }

    @Test
    void rejectsInvalidTypedStructureOptions() {
        assertInvalidStructure("""
                {"structureRuleId":"records","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"FORCE","type":"recordList",
                 "options":{"recordStartPattern":"^ID: (\\\\S+)$"}}
                """);
        assertInvalidStructure("""
                {"structureRuleId":"columns","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"FORCE","type":"table","options":{"columnNames":["name",""]}}
                """);
        assertInvalidStructure("""
                {"structureRuleId":"column-type","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"FORCE","type":"table","options":{"columnNames":["name",1]}}
                """);
        assertInvalidStructure("""
                {"structureRuleId":"separators","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"FORCE","type":"configStanza","options":{"configSeparators":[]}}
                """);
        assertInvalidStructure("""
                {"structureRuleId":"separator-value","selectors":[{"type":"COMMAND_ALIAS","value":"show x"}],
                 "mode":"FORCE","type":"configStanza","options":{"configSeparators":[""]}}
                """);
    }

    @Test
    void rejectsUnknownRuleOperationWithStableError() {
        String rules = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","rules":[{
                  "ruleId":"unsafe","blockRole":"UNSAFE","roleSelectors":[{"type":"JAVA","value":"run"}],
                  "extractors":[],"target":{"semanticKey":"device.identity.value","cardinality":"ONE",
                  "dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}
                }]}
                """;

        SemanticParserError error = assertThrows(SemanticParserError.class,
                () -> codec().decode(new SemanticParserSpecification(rules, emptyProjections())));

        assertEquals(SemanticParserError.INVALID_RULES, error.code());
    }

    @Test
    void rejectsDuplicateRuleIdsAndLongRegex() {
        String duplicate = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","rules":[
                  {"ruleId":"same","blockRole":"ONE","roleSelectors":[{"type":"COMMAND_ALIAS","value":"one"}],
                   "extractors":[],"target":{"semanticKey":"device.identity.one","cardinality":"ONE","dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}},
                  {"ruleId":"same","blockRole":"TWO","roleSelectors":[{"type":"COMMAND_ALIAS","value":"two"}],
                   "extractors":[],"target":{"semanticKey":"device.identity.two","cardinality":"ONE","dataType":"string","conflictPolicy":"HIGHEST_CONFIDENCE"}}
                ]}
                """;
        assertEquals(SemanticParserError.INVALID_RULES,
                assertThrows(SemanticParserError.class,
                        () -> codec().decode(new SemanticParserSpecification(duplicate, emptyProjections()))).code());

        String longRegex = duplicate.replace("\"same\",\"blockRole\":\"TWO\"",
                        "\"other\",\"blockRole\":\"TWO\"")
                .replace("\"COMMAND_ALIAS\",\"value\":\"one\"",
                        "\"CONTENT_REGEX\",\"value\":\"" + "x".repeat(2_049) + "\"");
        assertEquals(SemanticParserError.RESOURCE_LIMIT,
                assertThrows(SemanticParserError.class,
                        () -> codec().decode(new SemanticParserSpecification(longRegex, emptyProjections()))).code());
    }

    @Test
    void classifiesMissingRuleAndProjectionObjects() {
        String missingTarget = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","rules":[{
                  "ruleId":"missing","blockRole":"MISSING",
                  "roleSelectors":[{"type":"COMMAND_ALIAS","value":"show missing"}],"extractors":[]
                }]}
                """;
        SemanticParserError ruleError = assertThrows(SemanticParserError.class,
                () -> codec().decode(new SemanticParserSpecification(missingTarget, emptyProjections())));
        assertEquals(SemanticParserError.INVALID_RULES, ruleError.code());

        String missingFields = """
                {"schemaVersion":"1.0.0","catalogVersion":"test","profiles":[{
                  "projectionId":"missing","missingValuePolicy":"NULL"
                }]}
                """;
        String noRules = "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"test\",\"rules\":[]}";
        SemanticParserError projectionError = assertThrows(SemanticParserError.class,
                () -> codec().decode(new SemanticParserSpecification(noRules, missingFields)));
        assertEquals(SemanticParserError.INVALID_PROJECTIONS, projectionError.code());
    }

    @Test
    void validatesNamedGroupsForObjectAndSectionExtractors() {
        String valid = extractorRules("""
                {"type":"LINE_REGEX_OBJECT","pattern":"^SLOT (?<slot>[0-9]+)$",
                 "fields":{"slot":{"group":"slot","transforms":["integer"]}}}
                """);
        codec().decode(new SemanticParserSpecification(valid, emptyProjections()));

        String missingObjectGroup = extractorRules("""
                {"type":"LINE_REGEX_OBJECT","pattern":"^SLOT (?<slot>[0-9]+)$",
                 "fields":{"model":{"group":"model","transforms":[]}}}
                """);
        assertEquals(SemanticParserError.INVALID_RULES,
                assertThrows(SemanticParserError.class, () -> codec().decode(
                        new SemanticParserSpecification(missingObjectGroup, emptyProjections()))).code());

        String missingHeaderGroup = extractorRules("""
                {"type":"DELIMITED_SECTION","startPattern":"^(?<command>.+)$","headerGroup":"header"}
                """);
        assertEquals(SemanticParserError.INVALID_RULES,
                assertThrows(SemanticParserError.class, () -> codec().decode(
                        new SemanticParserSpecification(missingHeaderGroup, emptyProjections()))).code());
    }

    private static String extractorRules(String extractor) {
        return """
                {"schemaVersion":"1.0.0","catalogVersion":"test","rules":[{
                  "ruleId":"extract","blockRole":"TEST",
                  "roleSelectors":[{"type":"COMMAND_ALIAS","value":"show test"}],
                  "extractors":[%s],"target":{"semanticKey":"device.hardware.values",
                  "cardinality":"MANY","dataType":"object","conflictPolicy":"APPEND_DISTINCT"}
                }]}
                """.formatted(extractor);
    }

    private static SemanticCatalogCodec codec() {
        return new SemanticCatalogCodec(new CanonicalJson(), SemanticLimits.defaults());
    }

    private static void assertInvalidStructure(String structureRule) {
        SemanticParserError error = assertThrows(SemanticParserError.class, () -> codec().decode(
                new SemanticParserSpecification(structureRules(structureRule), emptyProjections())));
        assertEquals(SemanticParserError.INVALID_RULES, error.code());
    }

    private static String structureRules(String structureRule) {
        return """
                {"schemaVersion":"1.1.0","catalogVersion":"test",
                 "structureRules":[%s],"rules":[]}
                """.formatted(structureRule);
    }

    private static String emptyProjections() {
        return "{\"schemaVersion\":\"1.0.0\",\"catalogVersion\":\"test\",\"profiles\":[]}";
    }
}
