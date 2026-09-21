package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.Warning;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class StructuralSegmenter {

    private final KeyValueStructureParser keyValues;
    private final TableStructureParser tables;
    private final RecordStructureParser records;
    private final ConfigStanzaStructureParser configs;
    private final ListStructureParser lists;
    private final TextStructureParser text = new TextStructureParser();
    private final boolean enhanced;

    StructuralSegmenter() {
        this(false);
    }

    StructuralSegmenter(boolean enhanced) {
        this.enhanced = enhanced;
        keyValues = new KeyValueStructureParser(enhanced);
        tables = new TableStructureParser(enhanced);
        records = new RecordStructureParser(keyValues);
        configs = new ConfigStanzaStructureParser(enhanced);
        lists = new ListStructureParser(enhanced);
    }

    Result segment(List<StructureLine> lines, Directive directive, StructureBudget budget) {
        if (enhanced) {
            return segmentMixed(lines, directive, budget, 0);
        }
        if (lines.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        if ("TEXT".equals(directive.mode())) {
            return accepted(text.parse(lines, 0, 1), budget, List.of());
        }
        if ("FORCE".equals(directive.mode())) {
            Optional<StructureParseMatch> forced = force(lines, directive, 1);
            if (forced.isPresent()) {
                return accepted(forced, budget, List.of());
            }
            Warning warning = new Warning("STRUCTURE_HINT_MISMATCH", lines.getFirst().lineNumber());
            Section fallback = text.parse(lines, 0, 1).orElseThrow().section();
            return accepted(Optional.of(new StructureParseMatch(fallback, lines.size())), budget,
                    List.of(warning));
        }
        List<Section> sections = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        int cursor = 0;
        while (cursor < lines.size()) {
            if (lines.get(cursor).text().isBlank()) {
                cursor++;
                continue;
            }
            Optional<StructureParseMatch> match;
            try {
                match = auto(lines, cursor, sections.size() + 1, directive.configEnabled(),
                        directive.configSeparators());
            } catch (RuntimeException exception) {
                match = Optional.empty();
                warnings.add(new Warning("STRUCTURE_PARSE_FALLBACK", lines.get(cursor).lineNumber()));
            }
            if (match.isEmpty()) {
                int textEnd = nextStructureOffset(lines, cursor + 1, directive);
                match = text.parse(lines.subList(cursor, textEnd), 0, sections.size() + 1);
                match = match.map(value -> new StructureParseMatch(value.section(), textEnd));
            }
            Section section = match.orElseThrow().section();
            Optional<Section> accepted = budget.accept(section);
            if (accepted.isEmpty()) {
                break;
            }
            sections.add(accepted.get());
            if (match.get().nextOffset() <= cursor) {
                throw new IllegalStateException("structure parser did not advance");
            }
            cursor = match.get().nextOffset();
        }
        return new Result(List.copyOf(sections), List.copyOf(warnings));
    }

    private Result segmentMixed(List<StructureLine> lines, Directive directive, StructureBudget budget, int depth) {
        List<Section> sections = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        int cursor = 0;
        while (cursor < lines.size()) {
            if (lines.get(cursor).text().isBlank()) {
                cursor++;
                continue;
            }
            if (budget != null && !budget.canAcceptSection()) {
                budget.omitLines(lines.subList(cursor, lines.size()));
                break;
            }
            int index = sections.size() + 1;
            Optional<StructureParseMatch> match = Optional.empty();
            if (depth >= 16) {
                match = text.parse(lines, cursor, index);
                warnings.add(new Warning("STRUCTURE_DEPTH_LIMIT", lines.get(cursor).lineNumber()));
            } else if ("TEXT".equals(directive.mode())) {
                match = text.parse(lines, cursor, index);
            } else {
                try {
                    match = "FORCE".equals(directive.mode())
                            ? forceMixed(lines, cursor, index, directive, depth)
                            : autoMixed(lines, cursor, index, directive, depth);
                } catch (RuntimeException exception) {
                    warnings.add(new Warning("STRUCTURE_PARSE_FALLBACK", lines.get(cursor).lineNumber()));
                }
            }
            if (match.isEmpty()) {
                if ("FORCE".equals(directive.mode())) {
                    warnings.add(new Warning("STRUCTURE_HINT_MISMATCH", lines.get(cursor).lineNumber()));
                }
                int end = cursor + 1;
                while (end < lines.size() && !lines.get(end).text().isBlank()) {
                    Optional<StructureParseMatch> following;
                    try {
                        following = "FORCE".equals(directive.mode())
                                ? forceMixed(lines, end, index, directive, depth)
                                : autoMixed(lines, end, index, directive, depth);
                    } catch (RuntimeException exception) {
                        following = Optional.empty();
                    }
                    if (following.isPresent()) {
                        break;
                    }
                    end++;
                }
                StructureParseMatch fallback = text.parse(lines.subList(cursor, end), 0, index).orElseThrow();
                match = Optional.of(new StructureParseMatch(fallback.section(), end));
            }
            StructureParseMatch value = match.orElseThrow();
            Optional<Section> accepted = budget == null ? Optional.of(value.section()) : budget.accept(value.section());
            if (accepted.isEmpty()) {
                budget.omitLines(lines.subList(cursor, lines.size()));
                break;
            }
            sections.add(accepted.get());
            if (value.nextOffset() <= cursor) {
                throw new IllegalStateException("structure parser did not advance");
            }
            cursor = value.nextOffset();
        }
        return new Result(List.copyOf(sections), List.copyOf(warnings));
    }

    private Optional<StructureParseMatch> forceMixed(List<StructureLine> lines, int offset, int index,
            Directive directive, int depth) {
        return switch (directive.type()) {
            case "keyValue", "keyValueTree" -> keyValues.parse(lines, offset, index);
            case "table" -> tables.parse(lines, offset, index, directive.columnNames());
            case "recordList" -> mixedRecords(lines, offset, index, directive.recordStartPattern(), directive, depth);
            case "configStanza" -> configs.parse(lines, offset, index, true, directive.configSeparators());
            case "list" -> lists.parse(lines, offset, index);
            case "text" -> text.parse(lines, offset, index);
            default -> Optional.empty();
        };
    }

    private Optional<StructureParseMatch> autoMixed(List<StructureLine> lines, int offset, int index,
            Directive directive, int depth) {
        if (directive.configEnabled()) {
            Optional<StructureParseMatch> config = configs.parse(lines, offset, index, true, directive.configSeparators());
            if (config.isPresent()) {
                return config;
            }
        }
        Optional<StructureParseMatch> table = tables.parse(lines, offset, index, List.of());
        if (table.isPresent()) {
            return table;
        }
        Optional<StructureParseMatch> record = mixedRecords(lines, offset, index, null, directive, depth);
        if (record.isPresent()) {
            return record;
        }
        Optional<StructureParseMatch> key = keyValues.parse(lines, offset, index);
        return key.isPresent() ? key : lists.parse(lines, offset, index);
    }

    private Optional<StructureParseMatch> mixedRecords(List<StructureLine> lines, int offset, int index,
            Pattern pattern, Directive directive, int depth) {
        return records.parseMixed(lines, offset, index, pattern, body -> {
            Result result = segmentMixed(body, new Directive("AUTO", null, null, List.of(),
                    directive.configSeparators(), false), null, depth + 1);
            if (result.warnings().isEmpty() || result.sections().isEmpty()) {
                return result.sections();
            }
            List<Section> values = new ArrayList<>(result.sections());
            Section first = values.getFirst();
            List<Warning> combined = new ArrayList<>(first.warnings());
            combined.addAll(result.warnings());
            values.set(0, new Section(first.sectionIndex(), first.type(), first.startLine(), first.endLine(),
                    first.rawLines(), first.fields(), combined));
            return List.copyOf(values);
        });
    }

    private static Result accepted(
            Optional<StructureParseMatch> match,
            StructureBudget budget,
            List<Warning> warnings) {
        Optional<Section> section = match.flatMap(value -> budget.accept(value.section()));
        return new Result(section.map(List::of).orElse(List.of()), warnings);
    }

    private Optional<StructureParseMatch> auto(
            List<StructureLine> lines,
            int offset,
            int sectionIndex,
            boolean configEnabled,
            List<String> separators) {
        if (isExplicitTableStart(lines, offset)) {
            Optional<StructureParseMatch> table = tables.parse(lines, offset, sectionIndex, List.of());
            if (table.isPresent()) {
                return table;
            }
        }
        Optional<StructureParseMatch> record = records.parse(lines, offset, sectionIndex, null);
        if (record.isPresent()) {
            return record;
        }
        Optional<StructureParseMatch> key = keyValues.parse(lines, offset, sectionIndex);
        if (key.isPresent()) {
            return key;
        }
        Optional<StructureParseMatch> table = tables.parse(lines, offset, sectionIndex, List.of());
        if (table.isPresent()) {
            return table;
        }
        Optional<StructureParseMatch> config = configs.parse(
                lines, offset, sectionIndex, configEnabled, separators);
        if (config.isPresent()) {
            return config;
        }
        return lists.parse(lines, offset, sectionIndex);
    }

    private int nextStructureOffset(
            List<StructureLine> lines,
            int offset,
            Directive directive) {
        int cursor = offset;
        while (cursor < lines.size() && !lines.get(cursor).text().isBlank()) {
            try {
                if (auto(lines, cursor, 1, directive.configEnabled(), directive.configSeparators()).isPresent()) {
                    return cursor;
                }
            } catch (RuntimeException ignored) {
                // The caller records the fallback warning for the current interval.
            }
            cursor++;
        }
        return cursor;
    }

    private static boolean isExplicitTableStart(List<StructureLine> lines, int offset) {
        if (lines.get(offset).text().contains("|")) {
            return true;
        }
        if (lines.get(offset).text().replaceAll("\\s", "").matches("-{3,}")
                && offset + 1 < lines.size() && lines.get(offset + 1).text().contains("|")) {
            return true;
        }
        return offset + 1 < lines.size()
                && lines.get(offset + 1).text().matches("^\\s*-{3,}(?:\\s+-{3,})+\\s*$");
    }

    private Optional<StructureParseMatch> force(List<StructureLine> lines, Directive directive, int index) {
        return switch (directive.type()) {
            case "keyValue", "keyValueTree" -> keyValues.parse(lines, 0, index);
            case "table" -> tables.parse(lines, 0, index, directive.columnNames());
            case "recordList" -> records.parse(lines, 0, index, directive.recordStartPattern());
            case "configStanza" -> configs.parse(lines, 0, index, true, directive.configSeparators());
            case "list" -> lists.parse(lines, 0, index);
            case "text" -> text.parse(lines, 0, index);
            default -> Optional.empty();
        };
    }

    record Directive(
            String mode,
            String type,
            Pattern recordStartPattern,
            List<String> columnNames,
            List<String> configSeparators,
            boolean configEnabled) {
    }

    record Result(List<Section> sections, List<Warning> warnings) {
    }
}
