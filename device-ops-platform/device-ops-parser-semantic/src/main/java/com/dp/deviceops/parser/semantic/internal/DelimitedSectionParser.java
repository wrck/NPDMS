package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

final class DelimitedSectionParser {

    private final boolean enhanced;

    DelimitedSectionParser() {
        this(false);
    }

    DelimitedSectionParser(boolean enhanced) {
        this.enhanced = enhanced;
    }

    List<Section> parse(NormalizedEvidenceUnit unit, CompiledExtractor extractor) {
        List<SectionStart> starts = new ArrayList<>();
        for (int index = 0; index < unit.contentLines().size(); index++) {
            Matcher matcher = extractor.startPattern().matcher(unit.contentLines().get(index));
            if (matcher.find()) {
                String command = matcher.group(extractor.source().headerGroup());
                String title = command == null ? "" : command.strip();
                if (enhanced && title.codePoints().noneMatch(Character::isLetterOrDigit)) {
                    continue;
                }
                starts.add(new SectionStart(index, title));
            }
        }
        if (starts.isEmpty()) {
            return genericSection(unit, 0, unit.contentLines().size(), 0);
        }
        List<Section> result = new ArrayList<>();
        int firstStart = starts.getFirst().lineIndex();
        if (hasMeaningfulPreamble(unit, firstStart)) {
            result.addAll(genericSection(unit, 0, firstStart, 0));
        }
        for (int index = 0; index < starts.size(); index++) {
            SectionStart start = starts.get(index);
            int endExclusive = index + 1 < starts.size()
                    ? starts.get(index + 1).lineIndex()
                    : unit.contentLines().size();
            result.add(section(unit, index + 1, start.lineIndex(), endExclusive,
                    start.commandText(), start.commandText()));
        }
        return List.copyOf(result);
    }

    private static boolean hasMeaningfulPreamble(NormalizedEvidenceUnit unit, int endExclusive) {
        for (int index = 0; index < endExclusive; index++) {
            String line = unit.contentLines().get(index).strip();
            if (!line.isEmpty() && !line.equals(unit.commandText().strip())) {
                return true;
            }
        }
        return false;
    }

    private static List<Section> genericSection(
            NormalizedEvidenceUnit unit,
            int startInclusive,
            int endExclusive,
            int sectionIndex) {
        int start = startInclusive;
        int end = endExclusive;
        while (start < end && unit.contentLines().get(start).isBlank()) {
            start++;
        }
        while (end > start && unit.contentLines().get(end - 1).isBlank()) {
            end--;
        }
        if (start >= end) {
            return List.of();
        }
        return List.of(section(unit, sectionIndex, start, end,
                unit.commandText(), unit.commandText()));
    }

    private static Section section(
            NormalizedEvidenceUnit unit,
            int sectionIndex,
            int startInclusive,
            int endExclusive,
            String command,
            String header) {
        List<String> lines = List.copyOf(unit.contentLines().subList(startInclusive, endExclusive));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("command", command);
        value.put("header", header);
        value.put("lines", lines);
        value.put("startLine", startInclusive + 1);
        value.put("endLine", endExclusive);
        return new Section(sectionIndex, command, startInclusive + 1, endExclusive,
                lines, Collections.unmodifiableMap(new LinkedHashMap<>(value)));
    }

    record Section(
            int sectionIndex,
            String commandText,
            int lineStart,
            int lineEnd,
            List<String> lines,
            Map<String, Object> value) {
    }

    private record SectionStart(int lineIndex, String commandText) {
    }
}
