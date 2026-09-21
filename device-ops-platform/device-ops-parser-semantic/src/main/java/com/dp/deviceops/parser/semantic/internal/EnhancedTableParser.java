package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.TableColumn;
import com.dp.deviceops.parser.semantic.GenericContent.TableRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates column geometry against both header and data before consuming a table. */
final class EnhancedTableParser {

    private static final Pattern GAP = Pattern.compile(" {2,}");
    private static final Pattern DASH = Pattern.compile("-+");
    private static final Pattern EVENT = Pattern.compile(
            "^(?:\\d{4}[-/]\\d{2}[-/]\\d{2}\\s|\\d{1,2}:\\d{2}:\\d{2}\\s|"
                    + "\\[?(?:INFO|WARN(?:ING)?|ERROR|DEBUG|TRACE|NOTICE)\\b)", Pattern.CASE_INSENSITIVE);

    Optional<StructureParseMatch> parse(List<StructureLine> lines, int offset, int sectionIndex, List<String> hints) {
        int first = offset;
        if (border(lines.get(first).text())) {
            first++;
        }
        if (first >= lines.size()) {
            return Optional.empty();
        }
        if (lines.get(first).text().contains("|")) {
            return pipe(lines, offset, first, sectionIndex, hints);
        }
        return aligned(lines, offset, first, sectionIndex, hints);
    }

    private Optional<StructureParseMatch> pipe(
            List<StructureLine> lines, int offset, int first, int sectionIndex, List<String> hints) {
        List<String> firstCells = pipeCells(lines.get(first).text());
        int count = firstCells.size();
        if (count < 2 || separatorCells(firstCells) || (!hints.isEmpty() && hints.size() != count)) {
            return Optional.empty();
        }
        int cursor = first + 1;
        boolean separator = cursor < lines.size() && (border(lines.get(cursor).text())
                || separatorCells(pipeCells(lines.get(cursor).text())));
        boolean hasHeader = hints.isEmpty() && separator;
        List<String> labels = hasHeader ? firstCells : hints;
        List<List<String>> values = new ArrayList<>();
        if (!hasHeader) {
            values.add(firstCells);
        }
        if (separator) {
            cursor++;
        }
        while (cursor < lines.size()) {
            String text = lines.get(cursor).text();
            if (border(text)) {
                // A closing border belongs to this table. A following new header/table
                // must be reconsidered by the segmenter rather than eagerly consumed.
                cursor++;
                break;
            }
            if (text.isBlank() || !text.contains("|")) {
                break;
            }
            List<String> cells = pipeCells(text);
            if (cells.size() != count || separatorCells(cells)) {
                break;
            }
            values.add(cells);
            cursor++;
        }
        if (values.isEmpty() || (!hasHeader && hints.isEmpty() && values.size() < 2)) {
            return Optional.empty();
        }
        return Optional.of(match(lines, offset, cursor, sectionIndex, count, labels, values));
    }

    private Optional<StructureParseMatch> aligned(
            List<StructureLine> lines, int offset, int first, int sectionIndex, List<String> hints) {
        String header = expandTabs(lines.get(first).text());
        if (incompatible(header)) {
            return Optional.empty();
        }
        int afterHeader = first + 1;
        boolean separator = afterHeader < lines.size() && border(lines.get(afterHeader).text());
        int dataStart = hints.isEmpty() || separator ? afterHeader + (separator ? 1 : 0) : first;
        if (dataStart >= lines.size()) {
            return Optional.empty();
        }
        String firstData = expandTabs(lines.get(dataStart).text());
        if (incompatible(firstData) || border(firstData)) {
            return Optional.empty();
        }
        List<Span> spans = headerSpans(lines.get(first).text());
        // Actual grouped rules can define adjacent one-space labels. Decorative dash
        // runs never determine columns merely because one of them has length two.
        if (separator) {
            List<Span> ruled = ruledSpans(header, expandTabs(lines.get(afterHeader).text()));
            if (ruled.size() >= 2 && (spans.size() < 2 || ruled.size() == spans.size())) {
                spans = ruled;
            }
        }
        if (spans.size() < 2 || (!hints.isEmpty() && hints.size() != spans.size())) {
            return Optional.empty();
        }
        List<Integer> starts = boundaries(header, firstData, spans);
        if (starts.size() != spans.size()) {
            return Optional.empty();
        }
        List<String> labels = hints.isEmpty() ? slice(header, starts) : hints;
        if (labels.stream().anyMatch(java.util.Objects::isNull)) {
            return Optional.empty();
        }
        List<List<String>> values = new ArrayList<>();
        int cursor = dataStart;
        while (cursor < lines.size()) {
            String text = expandTabs(lines.get(cursor).text());
            if (border(text)) {
                if (!values.isEmpty()) {
                    cursor++;
                }
                break;
            }
            if (!values.isEmpty() && cursor + 2 < lines.size()
                    && border(lines.get(cursor + 1).text())
                    && !incompatible(expandTabs(lines.get(cursor + 2).text()))
                    && looksLikeHeader(text, starts, labels)) {
                break;
            }
            if (incompatible(text) || !fits(text, starts)
                    || (hints.isEmpty() && !lines.get(cursor).text().contains("\t")
                    && !GAP.matcher(text.strip()).find())) {
                break;
            }
            List<String> cells = slice(text, starts);
            // Empty cells are supported, but a single populated cell is not enough
            // evidence to turn arbitrary trailing prose into a table row.
            if (cells.getFirst() == null || cells.stream().filter(java.util.Objects::nonNull).count() < 2) {
                break;
            }
            values.add(cells);
            cursor++;
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(match(lines, offset, cursor, sectionIndex, starts.size(), labels, values));
    }

    private static List<Span> headerSpans(String raw) {
        String text = expandTabs(raw);
        List<Integer> cuts = new ArrayList<>();
        Matcher matcher = GAP.matcher(text);
        while (matcher.find()) {
            cuts.add(matcher.start());
            cuts.add(matcher.end());
        }
        // A single tab is still a column delimiter when expansion yields one space.
        int column = 0;
        for (int index = 0; index < raw.length(); index++) {
            if (raw.charAt(index) == '\t') {
                cuts.add(column);
                column += 4 - column % 4;
                cuts.add(column);
            } else {
                column++;
            }
        }
        cuts.add(0);
        cuts.add(text.length());
        cuts = cuts.stream().distinct().sorted().toList();
        List<Span> result = new ArrayList<>();
        for (int index = 1; index < cuts.size(); index++) {
            int start = cuts.get(index - 1);
            int end = cuts.get(index);
            while (start < end && text.charAt(start) == ' ') {
                start++;
            }
            while (end > start && text.charAt(end - 1) == ' ') {
                end--;
            }
            if (start < end) {
                result.add(new Span(start, end));
            }
        }
        return result;
    }

    private static List<Span> ruledSpans(String header, String rule) {
        Matcher matcher = DASH.matcher(rule);
        List<Span> spans = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.end() - matcher.start() < 3) {
                return List.of();
            }
            int start = matcher.start();
            int end = Math.min(matcher.end(), header.length());
            if (start >= end || header.substring(start, end).isBlank()) {
                return List.of();
            }
            while (start < end && header.charAt(start) == ' ') {
                start++;
            }
            while (end > start && header.charAt(end - 1) == ' ') {
                end--;
            }
            spans.add(new Span(start, end));
        }
        // Never slice a word using a rule that doesn't agree with the header.
        for (int index = 1; index < spans.size(); index++) {
            if (!wordBoundary(header, spans.get(index).start())) {
                return List.of();
            }
        }
        return spans;
    }

    private static List<Integer> boundaries(String header, String data, List<Span> spans) {
        List<Integer> starts = new ArrayList<>();
        starts.add(spans.getFirst().start());
        for (int index = 1; index < spans.size(); index++) {
            int candidate = spans.get(index).start();
            int previousEnd = spans.get(index - 1).end();
            // Use the shared whitespace gutter, not only header token starts: numeric
            // data may be right-aligned one or more columns left of a short label.
            while (candidate >= previousEnd && (!wordBoundary(header, candidate) || !wordBoundary(data, candidate))) {
                candidate--;
            }
            if (candidate < previousEnd || candidate <= starts.getLast()) {
                return List.of();
            }
            starts.add(candidate);
        }
        return List.copyOf(starts);
    }

    private static boolean looksLikeHeader(String text, List<Integer> starts, List<String> labels) {
        List<String> cells = slice(text, starts);
        if (cells.equals(labels)) {
            return true;
        }
        return cells.stream().allMatch(cell -> cell != null && Character.isUpperCase(cell.charAt(0))
                && cell.codePoints().anyMatch(Character::isLetter));
    }

    private static boolean fits(String text, List<Integer> starts) {
        int indent = starts.getFirst();
        if (text.length() < indent || !text.substring(0, indent).isBlank() && indent > 0) {
            return false;
        }
        for (int index = 1; index < starts.size(); index++) {
            if (!wordBoundary(text, starts.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean wordBoundary(String text, int index) {
        return index <= 0 || index >= text.length()
                || Character.isWhitespace(text.charAt(index - 1)) || Character.isWhitespace(text.charAt(index));
    }

    private static boolean incompatible(String text) {
        return text.isBlank() || text.contains("|") || KeyValueStructureParser.looksLikeKeyValue(text)
                || EVENT.matcher(text.stripLeading()).find();
    }

    private static boolean border(String text) {
        String compact = text.replaceAll("\\s", "");
        return compact.matches("-{3,}") || compact.matches("\\+(?:-{3,}\\+)+");
    }

    private static boolean separatorCells(List<String> cells) {
        return cells.size() >= 2 && cells.stream().allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private static List<String> pipeCells(String text) {
        String value = text.strip();
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|")) {
            value = value.substring(0, value.length() - 1);
        }
        return java.util.Arrays.stream(value.split("\\|", -1)).map(String::strip).toList();
    }

    private static String expandTabs(String text) {
        StringBuilder expanded = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\t') {
                expanded.append(" ".repeat(4 - expanded.length() % 4));
            } else {
                expanded.append(text.charAt(index));
            }
        }
        return expanded.toString();
    }

    private static List<String> slice(String text, List<Integer> starts) {
        List<String> cells = new ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            int start = Math.min(starts.get(index), text.length());
            int end = index + 1 == starts.size() ? text.length() : Math.min(starts.get(index + 1), text.length());
            String value = text.substring(start, end).strip();
            cells.add(value.isEmpty() ? null : value);
        }
        return Collections.unmodifiableList(cells);
    }

    private static StructureParseMatch match(List<StructureLine> lines, int offset, int cursor,
            int sectionIndex, int count, List<String> labels, List<List<String>> values) {
        List<TableColumn> columns = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String label = labels.isEmpty() || labels.get(index).isBlank() ? null : labels.get(index);
            columns.add(new TableColumn("column" + (index + 1), label, index));
        }
        List<TableRow> rows = new ArrayList<>();
        for (List<String> cells : values) {
            Map<String, String> row = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                String value = cells.get(index);
                row.put(columns.get(index).id(), value == null || value.isBlank() ? null : value);
            }
            rows.add(new TableRow(rows.size() + 1, row));
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("columns", columns);
        fields.put("rows", rows);
        fields.put("unparsedLines", List.of());
        List<StructureLine> consumed = lines.subList(offset, cursor);
        return new StructureParseMatch(new Section(sectionIndex, "table", consumed.getFirst().lineNumber(),
                consumed.getLast().lineNumber(), consumed.stream().map(StructureLine::text).toList(), fields, List.of()), cursor);
    }

    private record Span(int start, int end) {
    }
}
