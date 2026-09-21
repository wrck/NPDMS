package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry;
import com.dp.deviceops.parser.semantic.GenericContent.RecordValue;
import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.TableColumn;
import com.dp.deviceops.parser.semantic.GenericContent.TableRow;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundleCodec;
import com.dp.deviceops.parser.semantic.release.ParserReleaseManifest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EnhancedSemanticParserAcceptanceTest {
    @Test
    void sessionDecorationDoesNotHideStatisticsOrInventAnEmptyCommand() throws Exception {
        var result = parse();
        assertEquals("1.1.0", result.schemaVersion());
        assertEquals(50, result.genericContent().units().stream().filter(u -> u.nestingDepth() == 1).count());
        assertEquals(12, result.genericContent().units().stream().filter(u -> u.nestingDepth() == 1)
                .filter(u -> u.structureStatus() == GenericContent.StructureStatus.EMPTY).count());
        var session = unit(result, "show session statistic");
        assertNotEquals(GenericContent.StructureStatus.EMPTY, session.structureStatus());
        assertTrue(allEntries(session.sections()).anyMatch(e -> e.key().equals("Current total session number")
                && e.value().equals("4") && !e.children().isEmpty()));
    }

    @Test
    void temperatureTableHasFiveIntactColumnsAndValues() throws Exception {
        Section table = table(unit(parse(), "show environment"));
        assertEquals(List.of("Slot ID", "Board Name", "Board Temperature", "CPU Temperature", "Switch-chip Temperature"),
                columns(table).stream().map(TableColumn::label).toList());
        assertEquals("32", rows(table).getFirst().values().get("column4"));
        assertEquals("31", rows(table).getFirst().values().get("column5"));
    }

    @Test
    void allInterfaceBodiesAreKeptAndTheirAttributesAreAccessible() throws Exception {
        var result = unit(parse(), "show interface");
        var section = result.sections().stream().filter(s -> s.type().equals("recordList")).findFirst().orElseThrow();
        List<RecordValue> records = records(section);
        assertEquals(23, records.size());
        assertTrue(records.stream().allMatch(r -> !r.sections().isEmpty()));
        assertTrue(allEntries(records.getFirst().sections()).anyMatch(e -> e.key().strip().equals("MTU")));
        assertEquals(GenericContent.StructureStatus.PARTIAL, result.structureStatus());
        assertEquals(997, section.rawLines().stream().filter(s -> !s.isBlank()).count());
    }

    @Test
    void routeStatisticsAreNotConfusedWithColumnLabels() throws Exception {
        var unit = unit(parse(), "show ip route summary");
        assertEquals(List.of("Total", "Active", "Inactive"), allEntries(unit.sections())
                .filter(e -> List.of("Total", "Active", "Inactive").contains(e.key())).map(KeyValueEntry::key).toList());
        Section table = table(unit);
        assertEquals(List.of("Route Source", "Active", "Inactive"), columns(table).stream().map(TableColumn::label).toList());
        assertEquals(2, rows(table).size());
    }

    @Test
    void shortCpuArpAndMacTablesAreRecognized() throws Exception {
        var result = parse();
        for (String command : List.of("show cpu-usage detail", "show arp all", "show mac-address-table")) {
            assertEquals(1, rows(table(unit(result, command))).size(), command);
        }
    }

    @Test
    void everyNonblankBodyLineHasAStructuredOrTextSourceRange() throws Exception {
        var result = parse();
        var input = new com.dp.deviceops.parser.semantic.internal.CanonicalJson().mapper().readTree(
                Path.of("..", "parser-releases", "device-command-output-1.3.0", "input-real-sanitized.json").toFile());
        for (var unit : result.genericContent().units()) {
            String stdout = input.get("commandBlocks").get(unit.commandIndex() - 1).get("stdout").asText();
            String[] lines = stdout.split("\\r?\\n", -1);
            java.util.Set<Integer> covered = new java.util.HashSet<>();
            for (Section section : unit.sections()) {
                for (int i = 0; i < section.rawLines().size(); i++) {
                    covered.add(section.startLine() + i);
                }
            }
            for (int number = unit.sourceLineStart(); number <= unit.sourceLineEnd(); number++) {
                String text = lines[number - 1].strip();
                if (text.isEmpty() || text.equals(unit.commandText()) || text.matches("\\S+[>#]")) {
                    continue;
                }
                assertTrue(covered.contains(number), unit.commandText() + " uncovered source line " + number);
            }
        }
    }

    @Test
    void textModeRetainsAggregateBodyAndSourceIdentityRemainsStable() throws Exception {
        var enhanced = parse();
        var old = parse(false);
        assertEquals(old.inputSha256(), enhanced.inputSha256());
        assertEquals(1, unit(old, "show tech").sections().stream().mapToLong(s -> s.rawLines().size()).sum());
        assertTrue(unit(enhanced, "show tech").sections().stream().mapToLong(s -> s.rawLines().size()).sum() > 1900);
        assertEquals("1.3.0", old.parserVersion());
        assertEquals("1.4.0", enhanced.parserVersion());
    }

    private static SemanticParseResult parse() throws Exception {
        return parse(true);
    }

    private static SemanticParseResult parse(boolean enhanced) throws Exception {
        var source = new ParserReleaseBundleCodec().decode(Path.of("..", "parser-releases", "device-command-output-1.3.0"));
        var m = source.manifest();
        var manifest = new ParserReleaseManifest(m.manifestVersion(), m.logType(), enhanced ? "1.4.0" : m.releaseVersion(),
                m.inputAdapter(), m.inputSchemaVersion(), m.outputSchemaVersion(), enhanced ? "1.4.0" : m.engineVersion(),
                m.ruleVersion(), m.projectionVersion(), m.extension());
        var bundle = new ParserReleaseBundle(manifest, source.rulesJson(), source.projectionsJson(), source.modelProfilesJson(),
                source.ruleSetJsonByPath(), source.verificationCases());
        var plan = new ParserPlanCompiler().compile(bundle);
        return new DefaultDynamicSemanticParser().parse(plan, () -> new ByteArrayInputStream(
                source.verificationCases().getFirst().inputContent().getBytes(StandardCharsets.UTF_8)));
    }

    private static GenericContent.Unit unit(SemanticParseResult result, String name) {
        return result.genericContent().units().stream().filter(u -> u.commandText().equals(name)).findFirst().orElseThrow();
    }

    private static Section table(GenericContent.Unit unit) {
        return unit.sections().stream().filter(s -> s.type().equals("table")).findFirst().orElseThrow();
    }

    private static Stream<KeyValueEntry> allEntries(List<Section> sections) {
        return sections.stream().flatMap(section -> {
            if (section.type().startsWith("keyValue")) {
                return entries(section).stream().flatMap(EnhancedSemanticParserAcceptanceTest::entryTree);
            }
            if (section.type().equals("recordList")) {
                return records(section).stream().flatMap(record -> allEntries(record.sections()));
            }
            return Stream.empty();
        });
    }

    private static Stream<KeyValueEntry> entryTree(KeyValueEntry entry) {
        return Stream.concat(Stream.of(entry), entry.children().stream().flatMap(EnhancedSemanticParserAcceptanceTest::entryTree));
    }

    @SuppressWarnings("unchecked")
    private static List<KeyValueEntry> entries(Section section) { return (List<KeyValueEntry>) section.fields().get("entries"); }
    @SuppressWarnings("unchecked")
    private static List<RecordValue> records(Section section) { return (List<RecordValue>) section.fields().get("records"); }
    @SuppressWarnings("unchecked")
    private static List<TableColumn> columns(Section section) { return (List<TableColumn>) section.fields().get("columns"); }
    @SuppressWarnings("unchecked")
    private static List<TableRow> rows(Section section) { return (List<TableRow>) section.fields().get("rows"); }
}
