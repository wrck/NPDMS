package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry;
import com.dp.deviceops.parser.semantic.GenericContent.RecordValue;
import com.dp.deviceops.parser.semantic.GenericContent.Section;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecordStructureParser {

    private static final Pattern HORIZONTAL = Pattern.compile("^\\s*-{3,}\\s*$");

    private final KeyValueStructureParser keyValues;

    public RecordStructureParser(KeyValueStructureParser keyValues) {
        this.keyValues = Objects.requireNonNull(keyValues, "keyValues");
    }

    Optional<StructureParseMatch> parseMixed(
            List<StructureLine> lines, int offset, int sectionIndex, Pattern pattern,
            java.util.function.Function<List<StructureLine>, List<Section>> bodyParser) {
        if (offset >= lines.size() || lines.get(offset).text().isBlank()) {
            return Optional.empty();
        }
        int end = offset;
        while (end < lines.size() && !lines.get(end).text().isBlank()) {
            end++;
        }
        List<StructureLine> consumed = lines.subList(offset, end);
        List<RecordBlock> blocks;
        if (pattern != null) {
            consumed = lines.subList(offset, lines.size());
            blocks = forcedBlocks(consumed, pattern);
            if (blocks.isEmpty() || blocks.getFirst().start() != 0) {
                return Optional.empty();
            }
            end = lines.size();
        } else {
            int titleEnd = titleRangeEnd(lines, offset);
            if (titleEnd > offset) {
                consumed = lines.subList(offset, titleEnd);
                List<Integer> starts = new ArrayList<>();
                int indent = consumed.getFirst().indentationColumns();
                for (int i = 0; i < consumed.size(); i++) {
                    if (!consumed.get(i).text().isBlank() && consumed.get(i).indentationColumns() == indent) {
                        starts.add(i);
                    }
                }
                blocks = bounds(starts, consumed.size(), true, consumed);
                end = titleEnd;
            } else {
                blocks = repeatedFirstKeyBlocks(consumed);
                if (blocks.size() < 2) {
                    blocks = horizontalBlocks(consumed);
                    if (blocks.size() < 2 || blocks.stream().anyMatch(block ->
                            key(consumedLine(lines, offset, block.contentStart())) == null)) {
                        return Optional.empty();
                    }
                }
            }
        }
        List<RecordValue> values = new ArrayList<>();
        for (RecordBlock block : blocks) {
            List<StructureLine> body = consumed.subList(block.contentStart(), block.end());
            List<Section> sections = body.isEmpty() ? List.of() : bodyParser.apply(body);
            values.add(new RecordValue(values.size() + 1, block.identity(),
                    consumed.get(block.start()).lineNumber(), consumed.get(block.end() - 1).lineNumber(),
                    List.of(), sections));
        }
        Section section = new Section(sectionIndex, "recordList", consumed.getFirst().lineNumber(),
                consumed.getLast().lineNumber(), consumed.stream().map(StructureLine::text).toList(),
                Map.of("records", values), List.of());
        return Optional.of(new StructureParseMatch(section, end));
    }

    private static StructureLine consumedLine(List<StructureLine> lines, int offset, int index) {
        return lines.get(offset + index);
    }

    private static int titleRangeEnd(List<StructureLine> lines, int offset) {
        StructureLine first = lines.get(offset);
        if (key(first) != null || first.text().strip().matches("[-=*+|]+")) {
            return offset;
        }
        int next = offset + 1;
        while (next < lines.size() && lines.get(next).text().isBlank()) {
            next++;
        }
        int indent = first.indentationColumns();
        if (next >= lines.size() || lines.get(next).indentationColumns() <= indent) {
            return offset;
        }
        String prefix = first.content().split("\\s+", 2)[0];
        int end = next;
        while (end < lines.size()) {
            StructureLine line = lines.get(end);
            if (!line.text().isBlank() && line.indentationColumns() <= indent
                    && (line.indentationColumns() < indent || key(line) != null
                    || !line.content().startsWith(prefix + " "))) {
                break;
            }
            end++;
        }
        return end;
    }

    public Optional<StructureParseMatch> parse(
            List<StructureLine> lines,
            int offset,
            int sectionIndex,
            Pattern recordStartPattern) {
        if (offset < 0 || offset >= lines.size() || lines.get(offset).text().isBlank()) {
            return Optional.empty();
        }
        int cursor = offset;
        while (cursor < lines.size() && !lines.get(cursor).text().isBlank()) {
            cursor++;
        }
        List<StructureLine> consumed = lines.subList(offset, cursor);
        List<RecordBlock> blocks = recordStartPattern == null
                ? automaticBlocks(consumed)
                : forcedBlocks(consumed, recordStartPattern);
        if (blocks.size() < 2) {
            return Optional.empty();
        }
        List<RecordValue> records = new ArrayList<>();
        for (RecordBlock block : blocks) {
            records.add(toRecord(records.size() + 1, consumed, block));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("records", records);
        Section section = new Section(sectionIndex, "recordList",
                consumed.getFirst().lineNumber(), consumed.getLast().lineNumber(),
                consumed.stream().map(StructureLine::text).toList(), fields, List.of());
        return Optional.of(new StructureParseMatch(section, cursor));
    }

    private List<RecordBlock> automaticBlocks(List<StructureLine> lines) {
        List<RecordBlock> horizontal = horizontalBlocks(lines);
        if (horizontal.size() >= 2) {
            return horizontal;
        }
        List<RecordBlock> repeated = repeatedFirstKeyBlocks(lines);
        if (repeated.size() >= 2) {
            return repeated;
        }
        return repeatedTitleBlocks(lines);
    }

    private static List<RecordBlock> forcedBlocks(List<StructureLine> lines, Pattern pattern) {
        List<Integer> starts = new ArrayList<>();
        List<String> identities = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            Matcher matcher = pattern.matcher(lines.get(index).text().strip());
            if (matcher.matches()) {
                starts.add(index);
                identities.add(matcher.group("id"));
            }
        }
        List<RecordBlock> blocks = new ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index);
            int end = index + 1 < starts.size() ? starts.get(index + 1) : lines.size();
            blocks.add(new RecordBlock(start, end, start + 1, identities.get(index)));
        }
        return List.copyOf(blocks);
    }

    private static List<RecordBlock> horizontalBlocks(List<StructureLine> lines) {
        List<RecordBlock> blocks = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < lines.size(); index++) {
            if (HORIZONTAL.matcher(lines.get(index).text()).matches()) {
                if (index > start) {
                    blocks.add(new RecordBlock(start, index, start, null));
                }
                start = index + 1;
            }
        }
        if (start < lines.size()) {
            blocks.add(new RecordBlock(start, lines.size(), start, null));
        }
        return List.copyOf(blocks);
    }

    private static List<RecordBlock> repeatedFirstKeyBlocks(List<StructureLine> lines) {
        String firstKey = key(lines.getFirst());
        if (firstKey == null) {
            return List.of();
        }
        int baseIndent = lines.getFirst().indentationColumns();
        List<Integer> starts = new ArrayList<>(List.of(0));
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).indentationColumns() == baseIndent
                    && firstKey.equals(key(lines.get(index)))) {
                starts.add(index);
            }
        }
        return bounds(starts, lines.size(), false, lines);
    }

    private static List<RecordBlock> repeatedTitleBlocks(List<StructureLine> lines) {
        int baseIndent = lines.getFirst().indentationColumns();
        List<Integer> starts = new ArrayList<>();
        for (int index = 0; index + 1 < lines.size(); index++) {
            if (lines.get(index).indentationColumns() == baseIndent
                    && key(lines.get(index)) == null
                    && lines.get(index + 1).indentationColumns() > baseIndent
                    && key(lines.get(index + 1)) != null) {
                starts.add(index);
            }
        }
        return bounds(starts, lines.size(), true, lines);
    }

    private static List<RecordBlock> bounds(
            List<Integer> starts,
            int size,
            boolean titleIdentity,
            List<StructureLine> lines) {
        List<RecordBlock> blocks = new ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index);
            int end = index + 1 < starts.size() ? starts.get(index + 1) : size;
            blocks.add(new RecordBlock(start, end, titleIdentity ? start + 1 : start,
                    titleIdentity ? lines.get(start).text().strip() : null));
        }
        return List.copyOf(blocks);
    }

    private RecordValue toRecord(int index, List<StructureLine> lines, RecordBlock block) {
        List<KeyValueEntry> entries = List.of();
        if (block.contentStart() < block.end()) {
            List<StructureLine> content = lines.subList(block.contentStart(), block.end());
            entries = keyValues.parse(content, 0, 1)
                    .map(match -> keyValueEntries(match.section())).orElse(List.of());
        }
        return new RecordValue(index, block.identity(),
                lines.get(block.start()).lineNumber(), lines.get(block.end() - 1).lineNumber(),
                entries, List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<KeyValueEntry> keyValueEntries(Section section) {
        return (List<KeyValueEntry>) section.fields().get("entries");
    }

    private static String key(StructureLine line) {
        String content = line.content();
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current != ':' && current != '=') {
                continue;
            }
            if (current == ':' && index + 1 < content.length()
                    && !Character.isWhitespace(content.charAt(index + 1))) {
                continue;
            }
            String key = content.substring(0, index).strip();
            return key.codePoints().anyMatch(Character::isLetter) ? key : null;
        }
        return null;
    }

    private record RecordBlock(int start, int end, int contentStart, String identity) {
    }
}
