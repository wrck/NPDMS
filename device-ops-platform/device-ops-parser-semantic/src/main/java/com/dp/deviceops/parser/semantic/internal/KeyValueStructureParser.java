package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry;
import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.Warning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class KeyValueStructureParser {

    private final boolean enhanced;

    public KeyValueStructureParser() {
        this(false);
    }

    public KeyValueStructureParser(boolean enhanced) {
        this.enhanced = enhanced;
    }

    public Optional<StructureParseMatch> parse(
            List<StructureLine> lines,
            int offset,
            int sectionIndex) {
        if (offset < 0 || offset >= lines.size()) {
            return Optional.empty();
        }
        if (enhanced) {
            return parseEnhanced(lines, offset, sectionIndex);
        }
        List<EntryBuilder> roots = new ArrayList<>();
        List<EntryBuilder> stack = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        int cursor = offset;
        int baselineIndent = -1;
        int previousIndent = -1;
        boolean tree = false;
        while (cursor < lines.size()) {
            StructureLine line = lines.get(cursor);
            ParsedLine parsed = parseLine(line);
            if (parsed == null) {
                break;
            }
            int indent = line.indentationColumns();
            if (baselineIndent < 0) {
                baselineIndent = indent;
            }
            while (!stack.isEmpty() && stack.getLast().indent >= indent) {
                stack.removeLast();
            }
            EntryBuilder parent = stack.isEmpty() ? null : stack.getLast();
            if (parent == null) {
                roots.add(parsed.entry(indent));
                stack.add(roots.getLast());
            } else {
                EntryBuilder child = parsed.entry(indent);
                child.parent = parent;
                parent.children.add(child);
                parent.touch(line.lineNumber());
                stack.add(child);
                tree = true;
                if (indent - parent.indent > 4
                        || (previousIndent > indent && indent != parent.indent + 4)) {
                    warnings.add(new Warning("INDENTATION_GAP", line.lineNumber()));
                }
            }
            previousIndent = indent;
            cursor++;
        }
        if (cursor == offset) {
            return Optional.empty();
        }
        List<KeyValueEntry> entries = roots.stream().map(EntryBuilder::build).toList();
        Map<String, Object> data = materialize(entries);
        List<StructureLine> consumed = lines.subList(offset, cursor);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("entries", entries);
        fields.put("data", data);
        Section section = new Section(sectionIndex, tree ? "keyValueTree" : "keyValue",
                consumed.getFirst().lineNumber(), consumed.getLast().lineNumber(),
                consumed.stream().map(StructureLine::text).toList(),
                fields, warnings);
        return Optional.of(new StructureParseMatch(section, cursor));
    }

    /** Enhanced recognition shared with mixed segmentation and table boundary detection. */
    static boolean looksLikeKeyValue(String text) {
        return !EnhancedKeyValueLexer.parse(text).isEmpty();
    }

    private Optional<StructureParseMatch> parseEnhanced(
            List<StructureLine> lines, int offset, int sectionIndex) {
        List<EntryBuilder> roots = new ArrayList<>();
        List<EntryBuilder> stack = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        boolean tree = false;
        int cursor = offset;
        while (cursor < lines.size()) {
            StructureLine line = lines.get(cursor);
            List<EnhancedKeyValueLexer.Pair> pairs = EnhancedKeyValueLexer.parse(line.text());
            int indent = line.indentationColumns();
            if (pairs.isEmpty()) {
                EntryBuilder previous = stack.isEmpty() ? null : stack.getLast();
                if (previous == null || previous.value.isEmpty() || !previous.children.isEmpty()
                        || indent <= previous.indent
                        || !EnhancedKeyValueLexer.plainContinuation(line.content())) {
                    break;
                }
                previous.value += "\n" + line.content().strip();
                previous.touch(line.lineNumber());
                cursor++;
                continue;
            }
            while (!stack.isEmpty() && stack.getLast().indent >= indent) {
                stack.removeLast();
            }
            // Bound construction before recursive entry/materialized-data conversion.
            if (stack.size() >= 64) {
                warnings.add(new Warning("MAX_DEPTH_EXCEEDED", line.lineNumber()));
                break;
            }
            EntryBuilder parent = stack.isEmpty() ? null : stack.getLast();
            EntryBuilder last = null;
            for (EnhancedKeyValueLexer.Pair pair : pairs) {
                last = new EntryBuilder(pair.key(), pair.value(), line.lineNumber(), indent);
                if (parent == null) {
                    roots.add(last);
                } else {
                    last.parent = parent;
                    parent.children.add(last);
                    parent.touch(line.lineNumber());
                    tree = true;
                }
            }
            if (parent != null && indent - parent.indent > 4) {
                warnings.add(new Warning("INDENTATION_GAP", line.lineNumber()));
            }
            stack.add(last);
            cursor++;
        }
        if (cursor == offset) {
            return Optional.empty();
        }
        List<KeyValueEntry> entries = roots.stream().map(EntryBuilder::build).toList();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("entries", entries);
        fields.put("data", materialize(entries));
        List<StructureLine> consumed = lines.subList(offset, cursor);
        Section section = new Section(sectionIndex, tree ? "keyValueTree" : "keyValue",
                consumed.getFirst().lineNumber(), consumed.getLast().lineNumber(),
                consumed.stream().map(StructureLine::text).toList(), fields, warnings);
        return Optional.of(new StructureParseMatch(section, cursor));
    }

    private static ParsedLine parseLine(StructureLine line) {
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
            if (key.isEmpty() || key.codePoints().noneMatch(Character::isLetter)) {
                return null;
            }
            String value = content.substring(index + 1).strip();
            return new ParsedLine(key, value, line.lineNumber());
        }
        return null;
    }

    private static Map<String, Object> materialize(List<KeyValueEntry> entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (KeyValueEntry entry : entries) {
            Object value;
            if (entry.children().isEmpty()) {
                value = entry.value();
            } else {
                Map<String, Object> wrapped = new LinkedHashMap<>();
                if (!entry.value().isEmpty()) {
                    wrapped.put("nodeValue", entry.value());
                }
                wrapped.put("children", materialize(entry.children()));
                value = wrapped;
            }
            append(result, entry.key(), value);
        }
        return result;
    }

    private static void append(Map<String, Object> values, String key, Object value) {
        Object existing = values.get(key);
        if (existing == null) {
            values.put(key, value);
        } else if (existing instanceof List<?> list) {
            List<Object> repeated = new ArrayList<>(list);
            repeated.add(value);
            values.put(key, repeated);
        } else {
            values.put(key, new ArrayList<>(List.of(existing, value)));
        }
    }

    private record ParsedLine(String key, String value, int lineNumber) {
        EntryBuilder entry(int indent) {
            return new EntryBuilder(key, value, lineNumber, indent);
        }
    }

    private static final class EntryBuilder {
        private final String key;
        private String value;
        private final int startLine;
        private final int indent;
        private final List<EntryBuilder> children = new ArrayList<>();
        private EntryBuilder parent;
        private int endLine;

        private EntryBuilder(String key, String value, int startLine, int indent) {
            this.key = key;
            this.value = value;
            this.startLine = startLine;
            this.endLine = startLine;
            this.indent = indent;
        }

        private void touch(int lineNumber) {
            endLine = Math.max(endLine, lineNumber);
            if (parent != null) {
                parent.touch(lineNumber);
            }
        }

        private KeyValueEntry build() {
            return new KeyValueEntry(key, value, startLine, endLine,
                    children.stream().map(EntryBuilder::build).toList());
        }
    }
}
