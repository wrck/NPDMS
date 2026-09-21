package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.BlockObservation;
import com.dp.deviceops.parser.semantic.GenericContent;
import com.dp.deviceops.parser.semantic.GenericContent.ConfigStanza;
import com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry;
import com.dp.deviceops.parser.semantic.GenericContent.ListItem;
import com.dp.deviceops.parser.semantic.GenericContent.RecordValue;
import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.TableColumn;
import com.dp.deviceops.parser.semantic.GenericContent.TableRow;
import com.dp.deviceops.parser.semantic.ObservationStatus;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Extractor;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.ObjectField;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledExtractor;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledRule;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactExtractorEnhancedTest {

    @Test
    void reachesBothStatesWhenRealParserWrapsRepeatedKeysInRecords() {
        NormalizedEvidenceUnit unit = unit(List.of("Name: alpha", "State: up", "Name: beta", "State: down"));
        GenericStructureParser.Result structures = new GenericStructureParser(SemanticLimits.defaults())
                .parse(List.of(unit), new NestedEvidenceExpander.Expansion(List.of(), Map.of(), List.of()),
                        List.of(), List.of());
        assertEquals("recordList", structures.sectionsFor(unit).getFirst().type());

        List<SemanticFact> facts = extract(enhanced(SemanticLimits.defaults()), unit,
                rule(keyValue("State", List.of("trim"))), structures).facts();

        assertEquals(List.of("up", "down"), values(facts));
        assertEquals(List.of(2, 4), facts.stream().map(fact -> fact.source().lineStart()).toList());
    }

    @Test
    void defaultConstructorRetainsLegacyRecordInaccessibility() {
        NormalizedEvidenceUnit unit = unit(List.of("Name: alpha", "State: up", "Name: beta", "State: down"));
        GenericStructureParser.Result structures = new GenericStructureParser(SemanticLimits.defaults())
                .parse(List.of(unit), new NestedEvidenceExpander.Expansion(List.of(), Map.of(), List.of()),
                        List.of(), List.of());
        FactExtractor legacy = new FactExtractor(new SensitiveValueRedactor(), SemanticLimits.defaults());

        assertTrue(extract(legacy, unit, rule(keyValue("State", List.of())), structures).facts().isEmpty());
        assertEquals(List.of("up", "down"), values(extract(legacy, unit,
                rule(keyValue("State", List.of("trim"))), GenericStructureParser.Result.empty()).facts()));
    }

    @Test
    void traversesCanonicalRecordSectionsOnceAndKeepsNestedEvidenceAndTransforms() {
        NormalizedEvidenceUnit unit = nestedUnit();
        KeyValueEntry child = entry("State", " down ", 106);
        KeyValueEntry parent = new KeyValueEntry("State", " up ", 104, 106, List.of(child));
        Section kv = section("keyValueTree", 104, 106, Map.of("entries", List.of(parent)));
        RecordValue inner = new RecordValue(1, "inner", 103, 106, List.of(parent), List.of(kv));
        Section innerRecords = section("recordList", 103, 106, Map.of("records", List.of(inner)));
        RecordValue outer = new RecordValue(1, "outer", 101, 110, List.of(), List.of(innerRecords));
        GenericStructureParser.Result structures = structures(unit,
                section("recordList", 101, 110, Map.of("records", List.of(outer))));

        List<SemanticFact> facts = extract(enhanced(SemanticLimits.defaults()), unit,
                rule(keyValue("State", List.of("trim", "boolean-enable-disable"))), structures).facts();

        assertEquals(List.of(true, false), values(facts));
        assertEquals(List.of(104, 106), facts.stream().map(fact -> fact.source().lineStart()).toList());
        assertEquals(List.of(106, 106), facts.stream().map(fact -> fact.source().lineEnd()).toList());
        facts.forEach(fact -> {
            assertEquals(2, fact.source().commandIndex());
            assertEquals("show aggregate", fact.source().commandText());
            assertEquals(1, fact.source().nestingDepth());
            assertEquals("show synthetic", fact.source().nestedCommandText());
            assertEquals(7, fact.source().sectionIndex());
        });
    }

    @Test
    void fallsBackToRecordEntriesWhenChildrenContainNoKeyValueSection() {
        NormalizedEvidenceUnit unit = unit(List.of("synthetic record"));
        RecordValue record = new RecordValue(1, "alpha", 1, 3,
                List.of(entry("State", "up", 2)), List.of(section("text", 3, 3, Map.of())));

        List<SemanticFact> facts = extract(enhanced(SemanticLimits.defaults()), unit,
                rule(keyValue("State", List.of())), structures(unit,
                        section("recordList", 1, 3, Map.of("records", List.of(record))))).facts();

        assertEquals(List.of("up"), values(facts));
        assertEquals(2, facts.getFirst().source().lineStart());
    }

    @Test
    void reachesNestedTableConfigAndListSectionsInSourceOrder() {
        NormalizedEvidenceUnit unit = nestedUnit();
        Section table = table(103, List.of("Name", "State"), List.of("alpha", "up"));
        Section config = section("configStanza", 105, 106, Map.of("stanzas", List.of(
                new ConfigStanza("interface synthetic", 105, 106, List.of("interface synthetic", " enabled")))));
        Section list = section("list", 108, 109, Map.of("items", List.of(
                new ListItem(1, "first", 108, 108), new ListItem(2, "second", 109, 109))));
        RecordValue inner = new RecordValue(1, "inner", 102, 109, List.of(), List.of(table, config, list));
        RecordValue outer = new RecordValue(1, "outer", 101, 110, List.of(), List.of(
                section("recordList", 102, 109, Map.of("records", List.of(inner)))));
        GenericStructureParser.Result structures = structures(unit,
                section("recordList", 101, 110, Map.of("records", List.of(outer))));
        FactExtractor extractor = enhanced(SemanticLimits.defaults());

        List<SemanticFact> tables = extract(extractor, unit, rule(simple("TABLE")), structures).facts();
        assertEquals(List.of(Map.of("Name", "alpha", "State", "up")), values(tables));
        assertEquals(103, tables.getFirst().source().lineStart());
        assertEquals(104, tables.getFirst().source().lineEnd());
        List<SemanticFact> configs = extract(extractor, unit, rule(simple("CONFIG_STANZA")), structures).facts();
        assertEquals(List.of(Map.of("header", "interface synthetic", "startLine", 105, "endLine", 106,
                "lines", List.of("interface synthetic", " enabled"))), values(configs));
        assertEquals(105, configs.getFirst().source().lineStart());
        List<SemanticFact> lists = extract(extractor, unit, rule(simple("LIST")), structures).facts();
        assertEquals(List.of("first", "second"), values(lists));
        assertEquals(List.of(108, 109), lists.stream().map(fact -> fact.source().lineStart()).toList());
    }

    @Test
    void retainsEveryTableCellWhenDuplicateLabelsCollideWithGeneratedIds() {
        assertTableValues(List.of("Name", "Name", "column1"), List.of("A", "B", "C"));
    }

    @Test
    void resolvesSuffixAndGeneratedIdCollisionsDeterministically() {
        assertTableValues(List.of("Name", "Name", "column1", "column1_2", "column2"),
                List.of("A", "B", "C", "D", "E"));
    }

    @Test
    void boundsObjectRegexWorkBeforeConstructingTwentyThousandCandidateMaps() {
        CountingFields fields = new CountingFields();
        NormalizedEvidenceUnit unit = unit(List.of("7 ".repeat(20_000)));
        CompiledRule rule = rule(objectRegex(fields));

        SemanticParserError error = assertThrows(SemanticParserError.class,
                () -> extract(enhanced(limits(1)), unit, rule, GenericStructureParser.Result.empty()));

        assertEquals(SemanticParserError.RESOURCE_LIMIT, error.code());
        assertEquals("fact count exceeds limit", error.getMessage());
        assertEquals(1, fields.visits, "must stop before inspecting fields or constructing the second object");
    }

    @Test
    void appliesRemainingFactBudgetAcrossExtractorsBeforeObjectFieldWork() {
        CountingFields fields = new CountingFields();
        CompiledRule rule = rule(new Extractor("LINE_REGEX", "(7)", 1, null, null, List.of()),
                objectRegex(fields));

        SemanticParserError error = assertThrows(SemanticParserError.class, () -> extract(enhanced(limits(1)),
                unit(List.of("7")), rule, GenericStructureParser.Result.empty()));

        assertEquals(SemanticParserError.RESOURCE_LIMIT, error.code());
        assertEquals(0, fields.visits, "the preceding extractor already used the fact budget");
    }

    @Test
    void allowsExactlyTheFactLimitAndPreservesRegexTransforms() {
        CountingFields fields = new CountingFields();
        FactExtractor.ExtractionResult result = extract(enhanced(limits(1)), unit(List.of("7")),
                rule(objectRegex(fields)), GenericStructureParser.Result.empty());

        assertEquals(List.of(Map.of("number", 7L)), values(result.facts()));
        assertEquals(1, fields.visits);
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void invalidObjectMatchesDoNotConsumeTheAcceptedFactBudget() {
        Extractor object = new Extractor("LINE_REGEX_OBJECT", "(?<number>\\w+)", null, null, null,
                List.of(), Map.of("number", new ObjectField("number", List.of("integer"), true)), null, null);
        FactExtractor.ExtractionResult result = extract(enhanced(limits(1)), unit(List.of("invalid", "7")),
                rule(object), GenericStructureParser.Result.empty());

        assertEquals(List.of(Map.of("number", 7L)), values(result.facts()));
        assertEquals(List.of(Map.of("ruleId", "synthetic", "code", "INVALID_VALUE", "lineNumber", 1)),
                result.warnings());
    }

    private static void assertTableValues(List<String> labels, List<String> cells) {
        NormalizedEvidenceUnit unit = unit(List.of("synthetic table"));
        GenericStructureParser.Result structures = structures(unit, table(1, labels, cells));
        FactExtractor extractor = enhanced(SemanticLimits.defaults());
        List<SemanticFact> first = extract(extractor, unit, rule(simple("TABLE")), structures).facts();
        Map<?, ?> value = (Map<?, ?>) first.getFirst().value();
        assertEquals(cells.size(), value.size());
        assertEquals(cells, new ArrayList<>(value.values()));
        assertEquals(first, extract(extractor, unit, rule(simple("TABLE")), structures).facts());
    }

    private static FactExtractor enhanced(SemanticLimits limits) {
        return new FactExtractor(new SensitiveValueRedactor(), limits, true);
    }

    private static FactExtractor.ExtractionResult extract(FactExtractor extractor, NormalizedEvidenceUnit unit,
            CompiledRule rule, GenericStructureParser.Result structures) {
        BlockObservation observation = new BlockObservation(unit.unitIndex(), "synthetic", ObservationStatus.OBSERVED,
                1, 1, Math.max(1, unit.contentLines().size()), List.of(rule.source().ruleId()), List.of());
        return extractor.extract(List.of(unit), List.of(observation), List.of(rule), structures);
    }

    private static List<Object> values(List<SemanticFact> facts) {
        return facts.stream().map(SemanticFact::value).toList();
    }

    private static CompiledRule rule(Extractor... extractors) {
        SemanticCatalog.Rule source = new SemanticCatalog.Rule("synthetic", "synthetic", List.of(),
                List.of(extractors), new SemanticCatalog.Target("synthetic.values", "MANY", "OBJECT", "APPEND", null),
                List.of(), 1.0);
        return new CompiledRule(source, List.of(), source.extractors().stream()
                .map(extractor -> new CompiledExtractor(extractor,
                        extractor.pattern() == null ? null : Pattern.compile(extractor.pattern())))
                .toList());
    }

    private static Extractor simple(String type) {
        return new Extractor(type, null, null, null, null, List.of());
    }

    private static Extractor keyValue(String key, List<String> transforms) {
        return new Extractor("KEY_VALUE", null, null, key, null, transforms);
    }

    private static Extractor objectRegex(Map<String, ObjectField> fields) {
        return new Extractor("LINE_REGEX_OBJECT", "(?<number>\\d+)", null, null, null, List.of(), fields, null, null);
    }

    private static NormalizedEvidenceUnit unit(List<String> lines) {
        return new NormalizedEvidenceUnit(1, "COMMAND_OUTPUT", Map.of("commandText", "show synthetic"),
                lines, List.of(), false);
    }

    private static NormalizedEvidenceUnit nestedUnit() {
        return new NormalizedEvidenceUnit(5, "COMMAND_OUTPUT", Map.of("commandText", "show synthetic"),
                List.of("synthetic nested content"), List.of(), false,
                new EvidenceProvenance(2, "show aggregate", 1, "show synthetic", 7, 100));
    }

    private static GenericStructureParser.Result structures(NormalizedEvidenceUnit unit, Section... sections) {
        EvidenceProvenance provenance = unit.provenance();
        return new GenericStructureParser.Result(new GenericContent(List.of()), Map.of(
                new GenericStructureParser.UnitKey(provenance.commandIndex(), provenance.sectionIndex()), List.of(sections)));
    }

    private static Section section(String type, int start, int end, Map<String, Object> fields) {
        return new Section(1, type, start, end, List.of(), fields, List.of());
    }

    private static KeyValueEntry entry(String key, String value, int line) {
        return new KeyValueEntry(key, value, line, line, List.of());
    }

    private static Section table(int start, List<String> labels, List<String> cells) {
        List<TableColumn> columns = new ArrayList<>();
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (int index = 0; index < labels.size(); index++) {
            String id = "column" + (index + 1);
            columns.add(new TableColumn(id, labels.get(index), index));
            values.put(id, cells.get(index));
        }
        return section("table", start, start + 1,
                Map.of("columns", columns, "rows", List.of(new TableRow(1, values))));
    }

    private static SemanticLimits limits(int maxFacts) {
        return new SemanticLimits(2_000, 500, 2_048, 10_000, 16L << 20, 128L << 20, maxFacts, 64L << 20);
    }

    /** Counts real extraction work without timing assertions or production instrumentation. */
    private static final class CountingFields extends AbstractMap<String, ObjectField> {
        private int visits;

        @Override
        public Set<Entry<String, ObjectField>> entrySet() {
            visits++;
            return Map.of("number", new ObjectField("number", List.of("integer"), true)).entrySet();
        }
    }
}
