package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.TableColumn;
import com.dp.deviceops.parser.semantic.GenericContent.TableRow;
import com.dp.deviceops.parser.semantic.GenericContent.UnparsedLine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TableStructureParser {

    private static final Pattern DASH_SEGMENT = Pattern.compile("-+");

    private final boolean enhanced;

    public TableStructureParser() {
        this(false);
    }

    public TableStructureParser(boolean enhanced) {
        this.enhanced = enhanced;
    }

    public Optional<StructureParseMatch> parse(
            List<StructureLine> lines,
            int offset,
            int sectionIndex,
            List<String> columnNames) {
        if (offset < 0 || offset >= lines.size()) {
            return Optional.empty();
        }
        List<String> hints = columnNames == null ? List.of() : List.copyOf(columnNames);
        if (enhanced) {
            return new EnhancedTableParser().parse(lines, offset, sectionIndex, hints);
        }
        Optional<StructureParseMatch> pipe = parsePipe(lines, offset, sectionIndex, hints);
        return pipe.isPresent() ? pipe : parseAligned(lines, offset, sectionIndex, hints);
    }

    private Optional<StructureParseMatch> parsePipe(
            List<StructureLine> lines,
            int offset,
            int sectionIndex,
            List<String> hints) {
        if (!lines.get(offset).text().contains("|")
                && !(isHorizontalBorder(lines.get(offset).text())
                && offset + 1 < lines.size() && lines.get(offset + 1).text().contains("|"))) {
            return Optional.empty();
        }
        int cursor = offset;
        List<StructureLine> consumed = new ArrayList<>();
        while (cursor < lines.size() && !lines.get(cursor).text().isBlank()
                && (lines.get(cursor).text().contains("|")
                || isHorizontalBorder(lines.get(cursor).text()))) {
            consumed.add(lines.get(cursor++));
        }
        List<StructureLine> pipeLines = consumed.stream()
                .filter(line -> line.text().contains("|")).toList();
        if (pipeLines.size() < 2) {
            return Optional.empty();
        }
        List<List<String>> cells = pipeLines.stream().map(line -> pipeCells(line.text())).toList();
        boolean pipeSeparator = cells.size() >= 3 && isSeparator(cells.get(1));
        boolean borderSeparator = hasBorderBetweenFirstRows(consumed);
        boolean hasHeader = hints.isEmpty() && (pipeSeparator || borderSeparator);
        List<String> labels = hasHeader ? cells.getFirst() : hints;
        int columnCount = hasHeader ? labels.size() : cells.getFirst().size();
        if (columnCount < 2 || (!hints.isEmpty() && hints.size() != columnCount)) {
            return Optional.empty();
        }
        int dataStart = pipeSeparator ? 2 : hasHeader ? 1 : 0;
        if (cells.size() - dataStart < (hasHeader ? 1 : 2)) {
            return Optional.empty();
        }
        List<TableColumn> columns = columns(columnCount, labels);
        List<TableRow> rows = new ArrayList<>();
        List<UnparsedLine> unparsed = new ArrayList<>();
        for (int index = dataStart; index < cells.size(); index++) {
            StructureLine line = pipeLines.get(index);
            List<String> values = cells.get(index);
            if (isSeparator(values)) {
                continue;
            }
            if (values.size() != columnCount) {
                unparsed.add(new UnparsedLine(line.lineNumber(), line.text()));
                continue;
            }
            rows.add(row(rows.size() + 1, columns, values));
        }
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(match(sectionIndex, consumed, columns, rows, unparsed, cursor));
    }

    private static boolean hasBorderBetweenFirstRows(List<StructureLine> lines) {
        boolean firstPipeSeen = false;
        for (StructureLine line : lines) {
            if (line.text().contains("|")) {
                if (firstPipeSeen) {
                    return false;
                }
                firstPipeSeen = true;
            } else if (firstPipeSeen && isHorizontalBorder(line.text())) {
                return true;
            }
        }
        return false;
    }

    private Optional<StructureParseMatch> parseAligned(
            List<StructureLine> lines,
            int offset,
            int sectionIndex,
            List<String> hints) {
        int cursor = offset;
        List<StructureLine> consumed = new ArrayList<>();
        while (cursor < lines.size() && !lines.get(cursor).text().isBlank()
                && !lines.get(cursor).text().contains("|")) {
            consumed.add(lines.get(cursor++));
        }
        if (consumed.size() < (hints.isEmpty() ? 3 : 2)) {
            return Optional.empty();
        }

        boolean hasSeparator = consumed.size() >= 3 && isDashLine(consumed.get(1).text());
        List<Integer> starts;
        List<String> labels;
        int dataStart;
        if (hasSeparator) {
            starts = separatorStarts(consumed.getFirst().text(), consumed.get(1).text());
            labels = slice(consumed.getFirst().text(), starts);
            dataStart = 2;
        } else {
            starts = spacedStarts(consumed.getFirst().text());
            if (starts.size() < 2) {
                return Optional.empty();
            }
            if (hints.isEmpty()) {
                labels = slice(consumed.getFirst().text(), starts);
                dataStart = 1;
            } else {
                if (starts.size() != hints.size()) {
                    return Optional.empty();
                }
                labels = hints;
                dataStart = 0;
            }
        }
        if (!hints.isEmpty()) {
            if (starts.size() != hints.size()) {
                return Optional.empty();
            }
            labels = hints;
        }
        if (starts.size() < 2 || labels.size() != starts.size()) {
            return Optional.empty();
        }

        List<TableColumn> columns = columns(starts.size(), labels);
        List<TableRow> rows = new ArrayList<>();
        List<UnparsedLine> unparsed = new ArrayList<>();
        for (int index = dataStart; index < consumed.size(); index++) {
            if (hasSeparator && index == 1) {
                continue;
            }
            StructureLine line = consumed.get(index);
            List<String> values = slice(line.text(), starts);
            if (values.getFirst() == null || values.stream().allMatch(java.util.Objects::isNull)) {
                unparsed.add(new UnparsedLine(line.lineNumber(), line.text()));
                continue;
            }
            rows.add(row(rows.size() + 1, columns, values));
        }
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(match(sectionIndex, consumed, columns, rows, unparsed, cursor));
    }

    private static StructureParseMatch match(
            int sectionIndex,
            List<StructureLine> consumed,
            List<TableColumn> columns,
            List<TableRow> rows,
            List<UnparsedLine> unparsed,
            int nextOffset) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("columns", columns);
        fields.put("rows", rows);
        fields.put("unparsedLines", unparsed);
        Section section = new Section(sectionIndex, "table",
                consumed.getFirst().lineNumber(), consumed.getLast().lineNumber(),
                consumed.stream().map(StructureLine::text).toList(), fields, List.of());
        return new StructureParseMatch(section, nextOffset);
    }

    private static List<TableColumn> columns(int count, List<String> labels) {
        List<TableColumn> columns = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String label = labels.isEmpty() ? null : blankToNull(labels.get(index));
            columns.add(new TableColumn("column" + (index + 1), label, index));
        }
        return List.copyOf(columns);
    }

    private static TableRow row(int rowIndex, List<TableColumn> columns, List<String> values) {
        Map<String, String> mapped = new LinkedHashMap<>();
        for (int index = 0; index < columns.size(); index++) {
            mapped.put(columns.get(index).id(), blankToNull(values.get(index)));
        }
        return new TableRow(rowIndex, mapped);
    }

    private static List<String> pipeCells(String line) {
        String value = line.strip();
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|")) {
            value = value.substring(0, value.length() - 1);
        }
        return java.util.Arrays.stream(value.split("\\|", -1)).map(String::strip).toList();
    }

    private static boolean isSeparator(List<String> cells) {
        return !cells.isEmpty() && cells.stream().allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private static boolean isDashLine(String line) {
        return isHorizontalBorder(line);
    }

    private static boolean isHorizontalBorder(String line) {
        return line.replaceAll("\\s", "").matches("-{3,}");
    }

    private static List<Integer> separatorStarts(String header, String separator) {
        Matcher matcher = DASH_SEGMENT.matcher(separator);
        boolean onlySingleDashes = true;
        while (matcher.find()) {
            onlySingleDashes &= matcher.end() - matcher.start() == 1;
        }
        return onlySingleDashes ? spacedStarts(header) : dashStarts(separator);
    }

    private static List<Integer> dashStarts(String line) {
        List<Integer> starts = new ArrayList<>();
        Matcher matcher = DASH_SEGMENT.matcher(line);
        while (matcher.find()) {
            starts.add(matcher.start());
        }
        return List.copyOf(starts);
    }

    private static List<Integer> spacedStarts(String line) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int index = 0; index < line.length();) {
            if (!Character.isWhitespace(line.charAt(index))) {
                index++;
                continue;
            }
            int begin = index;
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            if (index - begin >= 2 && index < line.length()) {
                starts.add(index);
            }
        }
        return List.copyOf(starts);
    }

    private static List<String> slice(String line, List<Integer> starts) {
        List<String> values = new ArrayList<>(starts.size());
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index);
            int end = index + 1 < starts.size() ? starts.get(index + 1) : line.length();
            if (start >= line.length()) {
                values.add(null);
            } else {
                values.add(blankToNull(line.substring(start, Math.min(end, line.length()))));
            }
        }
        return java.util.Collections.unmodifiableList(values);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        return text.isEmpty() ? null : text;
    }
}
