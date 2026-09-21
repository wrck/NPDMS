package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry;
import com.dp.deviceops.parser.semantic.GenericContent.Section;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyValueStructureParserTest {

    private final KeyValueStructureParser parser = new KeyValueStructureParser();

    @Test
    void parsesColonEqualsDuplicateKeysAndOnlyTheFirstSeparator() {
        List<StructureLine> lines = lines(
                "Name: device",
                "Mode=active",
                "Description: first: second",
                "Mode=standby");

        StructureParseMatch match = parser.parse(lines, 0, 1).orElseThrow();
        Section section = match.section();
        List<KeyValueEntry> entries = entries(section);
        Map<String, Object> data = data(section);

        assertEquals("keyValue", section.type());
        assertEquals(4, match.nextOffset());
        assertEquals("first: second", entries.get(2).value());
        assertEquals(List.of("active", "standby"), data.get("Mode"));
        assertEquals("device", data.get("Name"));
    }

    @Test
    void rejectsTimesUrlsIpv6AndNumericPrefixes() {
        for (String value : List.of(
                "Compiled at 12:34:56 on 2026-08-29",
                "https://example.test:8443/path",
                "2001:db8::1",
                "1234: value")) {
            assertTrue(parser.parse(lines(value), 0, 1).isEmpty(), value);
        }
    }

    @Test
    void materializesIndentedTreesWithoutOverwritingValuesOrDuplicates() {
        StructureParseMatch match = parser.parse(lines(
                "snmp v1: in use",
                "    read community: [REDACTED]",
                "    trap: enabled",
                "trap: first",
                "trap: second",
                "parent: own-value",
                "        skipped: child-value"), 0, 1).orElseThrow();
        Section section = match.section();
        List<KeyValueEntry> entries = entries(section);
        Map<String, Object> data = data(section);

        assertEquals("keyValueTree", section.type());
        assertEquals("in use", entries.getFirst().value());
        assertEquals("[REDACTED]", entries.getFirst().children().getFirst().value());
        assertEquals(3, entries.getFirst().endLine());
        assertEquals(List.of("first", "second"), data.get("trap"));
        assertEquals("own-value", map(data.get("parent")).get("nodeValue"));
        assertEquals("child-value", map(map(data.get("parent")).get("children")).get("skipped"));
        assertEquals("INDENTATION_GAP", section.warnings().getFirst().code());
        assertEquals(7, section.warnings().getFirst().lineNumber());
    }

    @Test
    void expandsTabsToTheNextFourColumnStop() {
        Section section = parser.parse(lines("root:", "\tchild: value"), 0, 1)
                .orElseThrow().section();

        assertEquals("keyValueTree", section.type());
        assertEquals("child", entries(section).getFirst().children().getFirst().key());
        assertTrue(section.warnings().isEmpty());
    }

    private static List<StructureLine> lines(String... values) {
        java.util.ArrayList<StructureLine> lines = new java.util.ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            lines.add(new StructureLine(index + 1, values[index]));
        }
        return List.copyOf(lines);
    }

    @SuppressWarnings("unchecked")
    private static List<KeyValueEntry> entries(Section section) {
        return (List<KeyValueEntry>) section.fields().get("entries");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Section section) {
        return (Map<String, Object>) section.fields().get("data");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
