package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry;
import com.dp.deviceops.parser.semantic.GenericContent.Section;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnhancedKeyValueStructureParserTest {

    private final KeyValueStructureParser parser = new KeyValueStructureParser(true);

    @Test
    void parsesCompactStatisticsAndSingleSpaceSeparatedPairs() {
        Section section = parser.parse(lines(
                "State:up", "Total:4 Active:4 Inactive:0", "Input: 10 Output: 20"), 0, 7)
                .orElseThrow().section();

        assertEquals(List.of("State", "Total", "Active", "Inactive", "Input", "Output"),
                entries(section).stream().map(KeyValueEntry::key).toList());
        assertEquals(List.of("up", "4", "4", "0", "10", "20"),
                entries(section).stream().map(KeyValueEntry::value).toList());
        assertEquals(2, entries(section).get(3).startLine());
        assertEquals(7, section.sectionIndex());
    }

    @Test
    void parsesMultiwordKeysAfterSingleSpacesAndPreservesValuePunctuation() {
        Section section = parser.parse(lines(
                "Fan[0] status: Normal Fan gear: Low. Fan speed(R/min): 3132~3828."), 0, 1)
                .orElseThrow().section();

        assertEquals(List.of("Fan[0] status", "Fan gear", "Fan speed(R/min)"),
                entries(section).stream().map(KeyValueEntry::key).toList());
        assertEquals(List.of("Normal", "Low.", "3132~3828."),
                entries(section).stream().map(KeyValueEntry::value).toList());
    }

    @Test
    void parsesWideSpacingAndEqualsWithoutSplittingColonsInsideValues() {
        Section section = parser.parse(lines(
                "Input: 10  Output: 20", "Mode=active  Peer=standby",
                "Description: first: second", "Clock:10:20:30", "Address:fe80::1",
                "Link:https://example.test:8443/path", "Mac:aa:bb:cc:dd:ee:ff"), 0, 1)
                .orElseThrow().section();

        assertEquals(List.of("Input", "Output", "Mode", "Peer", "Description", "Clock", "Address", "Link", "Mac"),
                entries(section).stream().map(KeyValueEntry::key).toList());
        assertEquals("first: second", data(section).get("Description"));
        assertEquals("10:20:30", data(section).get("Clock"));
        assertEquals("fe80::1", data(section).get("Address"));
        assertEquals("https://example.test:8443/path", data(section).get("Link"));
        assertEquals("aa:bb:cc:dd:ee:ff", data(section).get("Mac"));
    }

    @Test
    void rejectsBareAddressesTimesUrlsAndEventPrefixes() {
        for (String text : List.of("10:20:30", "aa:bb:cc:dd:ee:ff", "fe80::1", "2001:db8::1",
                "https://example.test:8443/path", "Compiled at 12:34:56 on 2026-09-08",
                "1234: value", "2026-09-08 10:20:30 INFO daemon: started",
                "Sep 08 10:20:30 host daemon: started", "[INFO] daemon: started",
                "INFO daemon: started", "10:20:30 alarm: link down")) {
            assertTrue(parser.parse(lines(text), 0, 1).isEmpty(), "Must not invent a key for " + text);
        }
    }

    @Test
    void errorCounterLabelsAreNotMistakenForEventPrefixes() {
        var section = parser.parse(lines("Error   TCP                : 6 packet(s)",
                "Error   TCP_CHECKSUM       : 4 packet(s)"), 0, 1).orElseThrow().section();
        assertEquals(List.of("Error   TCP", "Error   TCP_CHECKSUM"),
                entries(section).stream().map(KeyValueEntry::key).toList());
    }

    @Test
    void quotedPunctuationAndEmbeddedAddressesAreNotSplitIntoExtraKeys() {
        var section = parser.parse(lines("Description: \"primary peer: active\"",
                "Message: remote https://example.test:8443/path",
                "Message: remote aa:bb:cc:dd:ee:ff"), 0, 1).orElseThrow().section();
        assertEquals(3, entries(section).size());
        assertEquals("\"primary peer: active\"", entries(section).getFirst().value());
    }

    @Test
    void retainsParentValueDuplicateChildrenAndIndentationRollback() {
        Section section = parser.parse(lines(
                "root:own-value", "  counter:1 counter:2", "\tleaf:on", "  peer:up", "root:second"), 0, 1)
                .orElseThrow().section();

        assertEquals("keyValueTree", section.type());
        List<KeyValueEntry> roots = entries(section);
        assertEquals(2, roots.size());
        assertEquals("own-value", roots.getFirst().value());
        assertEquals(4, roots.getFirst().endLine());
        assertEquals(List.of("counter", "counter", "peer"),
                roots.getFirst().children().stream().map(KeyValueEntry::key).toList());
        assertEquals("leaf", roots.getFirst().children().get(1).children().getFirst().key());
        assertEquals(2, ((List<?>) data(section).get("root")).size());
    }

    @Test
    void joinsOnlyDeeperPlainContinuationAndExtendsEvidence() {
        List<StructureLine> input = lines("Description: first line", "  wrapped detail",
                "  more detail", "State:up", "outside note", "Name:later");
        StructureParseMatch match = parser.parse(input, 0, 1).orElseThrow();

        assertEquals(4, match.nextOffset());
        assertEquals("first line\nwrapped detail\nmore detail", entries(match.section()).getFirst().value());
        assertEquals(3, entries(match.section()).getFirst().endLine());
        assertEquals(input.subList(0, 4).stream().map(StructureLine::text).toList(), match.section().rawLines());
    }

    @Test
    void doesNotSwallowTableListEventOrEmptyParentBodyAsContinuation() {
        for (String next : List.of("  Name    State", "  - separate item", "  ********",
                "  10:20:30 alarm: down", "  aa:bb:cc:dd:ee:ff", "  https://example.test/x")) {
            assertEquals(1, parser.parse(lines("Description: value", next), 0, 1)
                    .orElseThrow().nextOffset(), next);
        }
        assertEquals(1, parser.parse(lines("Parent:", "  independent prose"), 0, 1)
                .orElseThrow().nextOffset());
    }

    @Test
    void preservesEmptyAndDuplicateCompactValues() {
        Section section = parser.parse(lines("Mode:active Mode:standby", "Empty:"), 0, 1)
                .orElseThrow().section();
        assertEquals(List.of("active", "standby"), data(section).get("Mode"));
        assertEquals("", data(section).get("Empty"));
    }

    @Test
    void stopsBeforeMoreThanSixtyFourNestedEntriesWithWarning() {
        List<StructureLine> input = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            input.add(new StructureLine(index + 1, "  ".repeat(index) + "Node: value"));
        }
        StructureParseMatch match = parser.parse(input, 0, 1).orElseThrow();
        assertEquals(64, match.nextOffset());
        assertTrue(match.section().warnings().stream()
                .anyMatch(warning -> warning.code().equals("MAX_DEPTH_EXCEEDED") && warning.lineNumber() == 65));
        assertEquals(64, match.section().endLine());
    }

    @Test
    void rejectsIpv6ZoneAndPrefixLiteralsButKeepsThemAsExplicitFieldValues() {
        for (String value : List.of("fe80::1%eth0", "2001:db8::/64")) {
            assertTrue(parser.parse(lines(value), 0, 1).isEmpty(), value);
            assertEquals(value, data(parser.parse(lines("Address:" + value), 0, 1)
                    .orElseThrow().section()).get("Address"));
        }
    }

    private static List<StructureLine> lines(String... text) {
        List<StructureLine> result = new ArrayList<>();
        for (int index = 0; index < text.length; index++) {
            result.add(new StructureLine(index + 1, text[index]));
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static List<KeyValueEntry> entries(Section section) {
        return (List<KeyValueEntry>) section.fields().get("entries");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Section section) {
        return (Map<String, Object>) section.fields().get("data");
    }
}
