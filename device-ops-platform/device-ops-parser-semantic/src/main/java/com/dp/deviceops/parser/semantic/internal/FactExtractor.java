package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.BlockObservation;
import com.dp.deviceops.parser.semantic.ObservationStatus;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.GenericContent.ConfigStanza;
import com.dp.deviceops.parser.semantic.GenericContent.KeyValueEntry;
import com.dp.deviceops.parser.semantic.GenericContent.ListItem;
import com.dp.deviceops.parser.semantic.GenericContent.RecordValue;
import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.TableColumn;
import com.dp.deviceops.parser.semantic.GenericContent.TableRow;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog.Extractor;
import com.dp.deviceops.parser.semantic.internal.SemanticFact.SourceEvidence;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledExtractor;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class FactExtractor {

    private static final Pattern CAPACITY = Pattern.compile(
            "(?i)^([0-9]+(?:\\.[0-9]+)?)\\s*([KMGT]?)B?(?:\\s*bytes?)?$");
    private static final Pattern DURATION = Pattern.compile(
            "(?i)([0-9]+)\\s*(weeks?|days?|hours?|minutes?|seconds?)(?:\\(s\\))?");
    private static final Pattern CONFIG_SEPARATOR = Pattern.compile("^[!#;=-]+$");
    private static final Pattern TERMINAL_PROMPT = Pattern.compile("^\\S.*[>#$]\\s*$");

    private final SensitiveValueRedactor redactor;
    private final SemanticLimits limits;
    private final DelimitedSectionParser sectionParser;
    private final boolean enhanced;

    public FactExtractor(SensitiveValueRedactor redactor, SemanticLimits limits) {
        this(redactor, limits, false);
    }

    public FactExtractor(SensitiveValueRedactor redactor, SemanticLimits limits, boolean enhanced) {
        this.redactor = redactor;
        this.limits = limits;
        this.enhanced = enhanced;
        this.sectionParser = new DelimitedSectionParser(enhanced);
    }

    public ExtractionResult extract(
            List<NormalizedEvidenceUnit> units,
            List<BlockObservation> observations,
            List<CompiledRule> rules) {
        return extract(units, observations, rules, GenericStructureParser.Result.empty());
    }

    public ExtractionResult extract(
            List<NormalizedEvidenceUnit> units,
            List<BlockObservation> observations,
            List<CompiledRule> rules,
            GenericStructureParser.Result structures) {
        Map<String, CompiledRule> rulesById = rules.stream()
                .collect(Collectors.toMap(rule -> rule.source().ruleId(), Function.identity()));
        Map<Integer, NormalizedEvidenceUnit> unitsByIndex = units.stream()
                .collect(Collectors.toMap(NormalizedEvidenceUnit::unitIndex, Function.identity()));
        List<SemanticFact> facts = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        Set<Integer> redactedCommandIndexes = new HashSet<>();
        int[] redactedCount = {0};
        for (BlockObservation observation : observations) {
            if (!SetLike.PARSABLE.contains(observation.status())) {
                continue;
            }
            NormalizedEvidenceUnit unit = unitsByIndex.get(observation.commandIndex());
            for (String ruleId : observation.matchedRuleIds()) {
                CompiledRule compiledRule = rulesById.get(ruleId);
                if (compiledRule == null) {
                    continue;
                }
                SemanticCatalog.Rule rule = compiledRule.source();
                for (CompiledExtractor compiledExtractor : compiledRule.extractors()) {
                    Extractor extractor = compiledExtractor.source();
                    Consumer<Candidate> consumer = candidate -> {
                        SensitiveValueRedactor.Sanitized sanitized = redactor.isSensitiveKey(extractor.key())
                                ? new SensitiveValueRedactor.Sanitized("[REDACTED]", true)
                                : redactor.sanitize(candidate.rawValue());
                        if (sanitized.redacted() && candidate.rawValue() instanceof String) {
                            warnings.add(warning(rule.ruleId(), "REDACTED_VALUE", candidate.lineStart()));
                            redactedCommandIndexes.add(unit.provenance().commandIndex());
                            redactedCount[0]++;
                            return;
                        }
                        try {
                            Object value = transform(sanitized.value(), extractor.transforms());
                            if (sanitized.redacted()) {
                                warnings.add(warning(rule.ruleId(), "REDACTED_VALUE", candidate.lineStart()));
                                redactedCommandIndexes.add(unit.provenance().commandIndex());
                                redactedCount[0]++;
                            }
                            double confidence = rule.confidence() == null ? 1 : rule.confidence();
                            facts.add(new SemanticFact(rule.target().semanticKey(), value,
                                    rule.target().dataType(), rule.target().unit(),
                                    sanitized.redacted() ? ObservationStatus.PARTIAL : ObservationStatus.OBSERVED,
                                    confidence, rule.target().cardinality(), rule.target().conflictPolicy(),
                                    sourceEvidence(unit, candidate), rule.ruleId()));
                            if (facts.size() > limits.maxFacts()) {
                                throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT,
                                        "fact count exceeds limit");
                            }
                        } catch (SemanticParserError error) {
                            throw error;
                        } catch (RuntimeException error) {
                            warnings.add(warning(rule.ruleId(), "INVALID_VALUE", candidate.lineStart()));
                        }
                    };
                    CandidateSink sink = new CandidateSink(enhanced, consumer, facts);
                    candidates(unit, compiledExtractor, warnings, rule.ruleId(), structures, sink);
                    sink.finish();
                }
            }
        }
        return new ExtractionResult(List.copyOf(facts), List.copyOf(warnings), redactedCount[0],
                Set.copyOf(redactedCommandIndexes));
    }

    private static SourceEvidence sourceEvidence(NormalizedEvidenceUnit unit, Candidate candidate) {
        EvidenceProvenance provenance = unit.provenance();
        if (provenance.nestingDepth() == 0) {
            return new SourceEvidence(provenance.commandIndex(), provenance.commandText(),
                    candidate.lineStart(), candidate.lineEnd());
        }
        return new SourceEvidence(provenance.commandIndex(), provenance.commandText(),
                provenance.absoluteLine(candidate.lineStart()),
                provenance.absoluteLine(candidate.lineEnd()),
                provenance.nestingDepth(), provenance.nestedCommandText(), provenance.sectionIndex());
    }

    private void candidates(
            NormalizedEvidenceUnit unit,
            CompiledExtractor compiled,
            List<Map<String, Object>> warnings,
            String ruleId,
            GenericStructureParser.Result structures,
            CandidateSink sink) {
        Extractor extractor = compiled.source();
        switch (extractor.type()) {
            case "LINE_REGEX" -> regexCandidates(unit, compiled, sink);
            case "LINE_REGEX_OBJECT" -> objectRegexCandidates(unit, compiled, warnings, ruleId, sink);
            case "KEY_VALUE" -> structureKeyValueCandidates(unit, extractor, structures, sink);
            case "STATUS_TEXT" -> statusCandidates(unit, compiled, sink);
            case "TABLE" -> structureTableCandidates(unit, structures, sink);
            case "CONFIG_STANZA" -> structureStanzaCandidates(unit, structures, sink);
            case "RECORD_LIST" -> structureRecordCandidates(unit, structures, sink);
            case "LIST" -> structureListCandidates(unit, structures, sink);
            case "STRUCTURE_SECTION" -> structureSectionCandidates(unit, structures, sink);
            case "DELIMITED_SECTION" -> delimitedSectionCandidates(unit, compiled, sink);
            default -> { }
        }
    }

    private void structureKeyValueCandidates(
            NormalizedEvidenceUnit unit,
            Extractor extractor,
            GenericStructureParser.Result structures,
            CandidateSink sink) {
        if (structures.sectionsFor(unit).isEmpty()) {
            keyValueCandidates(unit, extractor, sink);
            return;
        }
        String expected = extractor.key().strip();
        visitStructures(structures.sectionsFor(unit), section -> {
            if (isKeyValueSection(section)) {
                collectKeyValueCandidates(unit, keyValueEntries(section), expected, sink);
            }
        }, record -> {
            // Enhanced records use sections as canonical; old records can contain entries only.
            if (record.sections().stream().noneMatch(FactExtractor::isKeyValueSection)) {
                collectKeyValueCandidates(unit, record.entries(), expected, sink);
            }
        });
    }

    private static boolean isKeyValueSection(Section section) {
        return "keyValue".equals(section.type()) || "keyValueTree".equals(section.type());
    }

    private static void collectKeyValueCandidates(
            NormalizedEvidenceUnit unit,
            List<KeyValueEntry> entries,
            String expected,
            CandidateSink sink) {
        Deque<Iterator<KeyValueEntry>> stack = new ArrayDeque<>();
        stack.push(entries.iterator());
        while (!stack.isEmpty()) {
            Iterator<KeyValueEntry> iterator = stack.peek();
            if (!iterator.hasNext()) {
                stack.pop();
                continue;
            }
            KeyValueEntry entry = iterator.next();
            if (entry.key().strip().equalsIgnoreCase(expected)) {
                sink.emit(entry.value(), localLine(unit, entry.startLine()), localLine(unit, entry.endLine()));
            }
            if (!entry.children().isEmpty()) {
                stack.push(entry.children().iterator());
            }
        }
    }

    /** Depth-first traversal without flattening the tree or using the Java call stack. */
    private void visitStructures(List<Section> sections, Consumer<Section> sectionConsumer,
            Consumer<RecordValue> recordConsumer) {
        Deque<Iterator<?>> stack = new ArrayDeque<>();
        stack.push(sections.iterator());
        while (!stack.isEmpty()) {
            Iterator<?> iterator = stack.peek();
            if (!iterator.hasNext()) {
                stack.pop();
                continue;
            }
            Object item = iterator.next();
            if (item instanceof Section section) {
                sectionConsumer.accept(section);
                if (enhanced && "recordList".equals(section.type())) {
                    stack.push(records(section).iterator());
                }
            } else if (item instanceof RecordValue record) {
                recordConsumer.accept(record);
                stack.push(record.sections().iterator());
            }
        }
    }

    private void structureTableCandidates(
            NormalizedEvidenceUnit unit,
            GenericStructureParser.Result structures,
            CandidateSink sink) {
        if (structures.sectionsFor(unit).isEmpty()) {
            tableCandidates(unit, sink);
            return;
        }
        visitStructures(structures.sectionsFor(unit), section -> {
            if (!"table".equals(section.type()) || tableRows(section).isEmpty()) {
                return;
            }
            sink.checkCapacity();
            List<TableColumn> columns = tableColumns(section);
            Map<String, Long> labelCounts = columns.stream()
                    .filter(column -> column.label() != null && !column.label().isBlank())
                    .collect(Collectors.groupingBy(TableColumn::label, LinkedHashMap::new, Collectors.counting()));
            List<String> names = new ArrayList<>();
            for (TableColumn column : columns) {
                names.add(column.label() != null && labelCounts.getOrDefault(column.label(), 0L) == 1
                        ? column.label() : column.id());
            }
            if (enhanced) {
                names = uniqueFieldNames(names);
            }
            for (TableRow row : tableRows(section)) {
                sink.checkCapacity();
                Map<String, Object> values = new LinkedHashMap<>();
                for (int index = 0; index < columns.size(); index++) {
                    values.put(names.get(index), row.values().get(columns.get(index).id()));
                }
                sink.emit(values, localLine(unit, section.startLine()), localLine(unit, section.endLine()));
            }
        }, record -> { });
    }

    private static List<String> uniqueFieldNames(List<String> preferred) {
        Set<String> reserved = new HashSet<>(preferred);
        Set<String> used = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String base : preferred) {
            String name = base;
            int suffix = 2;
            while (used.contains(name)) {
                do {
                    name = base + "_" + suffix++;
                } while (reserved.contains(name));
            }
            used.add(name);
            result.add(name);
        }
        return result;
    }

    private void structureStanzaCandidates(
            NormalizedEvidenceUnit unit,
            GenericStructureParser.Result structures,
            CandidateSink sink) {
        if (structures.sectionsFor(unit).isEmpty()) {
            stanzaCandidates(unit, sink);
            return;
        }
        visitStructures(structures.sectionsFor(unit), section -> {
            if (!"configStanza".equals(section.type())) {
                return;
            }
            for (ConfigStanza stanza : configStanzas(section)) {
                sink.checkCapacity();
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("header", stanza.header());
                value.put("startLine", stanza.startLine());
                value.put("endLine", stanza.endLine());
                value.put("lines", stanza.lines());
                sink.emit(value, localLine(unit, stanza.startLine()), localLine(unit, stanza.endLine()));
            }
        }, record -> { });
    }

    private void structureRecordCandidates(
            NormalizedEvidenceUnit unit,
            GenericStructureParser.Result structures,
            CandidateSink sink) {
        visitStructures(structures.sectionsFor(unit), section -> {
            if ("recordList".equals(section.type())) {
                for (RecordValue record : records(section)) {
                    sink.emit(record, localLine(unit, record.startLine()), localLine(unit, record.endLine()));
                }
            }
        }, record -> { });
    }

    private void structureListCandidates(
            NormalizedEvidenceUnit unit,
            GenericStructureParser.Result structures,
            CandidateSink sink) {
        visitStructures(structures.sectionsFor(unit), section -> {
            if ("list".equals(section.type())) {
                for (ListItem item : listItems(section)) {
                    sink.emit(item.value(), localLine(unit, item.startLine()), localLine(unit, item.endLine()));
                }
            }
        }, record -> { });
    }

    private static void structureSectionCandidates(
            NormalizedEvidenceUnit unit,
            GenericStructureParser.Result structures,
            CandidateSink sink) {
        // This extractor intentionally emits top-level containers, not their contents again.
        for (Section section : structures.sectionsFor(unit)) {
            sink.checkCapacity();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("sectionIndex", section.sectionIndex());
            value.put("type", section.type());
            value.put("startLine", section.startLine());
            value.put("endLine", section.endLine());
            value.put("rawLines", section.rawLines());
            value.putAll(section.fields());
            value.put("warnings", section.warnings());
            sink.emit(value, localLine(unit, section.startLine()), localLine(unit, section.endLine()));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<KeyValueEntry> keyValueEntries(Section section) {
        return (List<KeyValueEntry>) section.fields().getOrDefault("entries", List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<TableColumn> tableColumns(Section section) {
        return (List<TableColumn>) section.fields().getOrDefault("columns", List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<TableRow> tableRows(Section section) {
        return (List<TableRow>) section.fields().getOrDefault("rows", List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<ConfigStanza> configStanzas(Section section) {
        return (List<ConfigStanza>) section.fields().getOrDefault("stanzas", List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<RecordValue> records(Section section) {
        return (List<RecordValue>) section.fields().getOrDefault("records", List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<ListItem> listItems(Section section) {
        return (List<ListItem>) section.fields().getOrDefault("items", List.of());
    }

    private static int localLine(NormalizedEvidenceUnit unit, int absoluteLine) {
        return Math.subtractExact(absoluteLine, unit.provenance().lineOffset());
    }

    private void objectRegexCandidates(
            NormalizedEvidenceUnit unit,
            CompiledExtractor compiled,
            List<Map<String, Object>> warnings,
            String ruleId,
            CandidateSink sink) {
        for (int index = 0; index < unit.contentLines().size(); index++) {
            Matcher matcher = compiled.pattern().matcher(unit.contentLines().get(index));
            while (matcher.find()) {
                sink.checkCapacity();
                Map<String, Object> object = new LinkedHashMap<>();
                boolean missingRequired = false;
                boolean invalidValue = false;
                for (Map.Entry<String, SemanticCatalog.ObjectField> entry
                        : compiled.source().fields().entrySet()) {
                    SemanticCatalog.ObjectField field = entry.getValue();
                    String captured = matcher.group(field.group());
                    if (captured == null || captured.isBlank()) {
                        if (!Boolean.FALSE.equals(field.required())) {
                            missingRequired = true;
                            break;
                        }
                        continue;
                    }
                    try {
                        object.put(entry.getKey(), transform(captured, field.transforms()));
                    } catch (RuntimeException error) {
                        warnings.add(warning(ruleId, "INVALID_VALUE", index + 1));
                        missingRequired = true;
                        invalidValue = true;
                        break;
                    }
                }
                if (missingRequired) {
                    if (!invalidValue) {
                        warnings.add(warning(ruleId, "MISSING_OBJECT_FIELD", index + 1));
                    }
                    continue;
                }
                sink.emit(object, index + 1, index + 1);
            }
        }
    }

    private void regexCandidates(NormalizedEvidenceUnit unit, CompiledExtractor compiled, CandidateSink sink) {
        Extractor extractor = compiled.source();
        Pattern pattern = compiled.pattern();
        for (int index = 0; index < unit.contentLines().size(); index++) {
            String line = unit.contentLines().get(index);
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                sink.checkCapacity();
                int group = extractor.group() == null ? 1 : extractor.group();
                Object value = group <= matcher.groupCount() ? matcher.group(group) : matcher.group();
                sink.emit(value, index + 1, index + 1);
            }
        }
    }

    private void keyValueCandidates(NormalizedEvidenceUnit unit, Extractor extractor, CandidateSink sink) {
        String expected = extractor.key().strip().toLowerCase(Locale.ROOT);
        for (int index = 0; index < unit.contentLines().size(); index++) {
            String line = unit.contentLines().get(index);
            int separator = line.indexOf(':');
            if (separator > 0 && line.substring(0, separator).strip().toLowerCase(Locale.ROOT).equals(expected)) {
                sink.checkCapacity();
                sink.emit(line.substring(separator + 1), index + 1, index + 1);
            }
        }
    }

    private void statusCandidates(NormalizedEvidenceUnit unit, CompiledExtractor compiled, CandidateSink sink) {
        Extractor extractor = compiled.source();
        Pattern pattern = compiled.pattern();
        for (int index = 0; index < unit.contentLines().size(); index++) {
            String line = unit.contentLines().get(index);
            if (pattern.matcher(line).find()) {
                sink.checkCapacity();
                sink.emit(extractor.value() == null ? line.strip() : extractor.value(), index + 1, index + 1);
            }
        }
    }

    private void tableCandidates(NormalizedEvidenceUnit unit, CandidateSink sink) {
        int headerIndex = tableHeaderIndex(unit.contentLines());
        if (headerIndex < 0) {
            return;
        }
        List<String> headers = columns(unit.contentLines().get(headerIndex));
        if (headers.size() < 2) {
            return;
        }
        if (enhanced) {
            headers = uniqueFieldNames(headers);
        }
        for (int index = headerIndex + 1; index < unit.contentLines().size(); index++) {
            String line = unit.contentLines().get(index);
            if (line.isBlank() || line.matches("^[=-]+(?:\\s+[=-]+)*$")) {
                continue;
            }
            List<String> values = columns(line);
            if (values.size() < 2) {
                continue;
            }
            sink.checkCapacity();
            Map<String, Object> row = new LinkedHashMap<>();
            for (int column = 0; column < Math.min(headers.size(), values.size()); column++) {
                row.put(headers.get(column), values.get(column));
            }
            sink.emit(row, index + 1, index + 1);
        }
    }

    private void stanzaCandidates(NormalizedEvidenceUnit unit, CandidateSink sink) {
        List<IndexedLine> lines = configurationLines(unit);
        int index = 0;
        while (index < lines.size()) {
            IndexedLine indexedHeader = lines.get(index);
            String header = indexedHeader.text();
            if (header.isBlank() || Character.isWhitespace(header.charAt(0))) {
                index++;
                continue;
            }
            sink.checkCapacity();
            int end = index;
            List<String> stanzaLines = new ArrayList<>();
            stanzaLines.add(header);
            while (end + 1 < lines.size()) {
                String next = lines.get(end + 1).text();
                if (!next.isBlank() && !Character.isWhitespace(next.charAt(0))) {
                    break;
                }
                end++;
                stanzaLines.add(next);
            }
            Map<String, Object> stanza = new LinkedHashMap<>();
            stanza.put("header", header);
            stanza.put("startLine", indexedHeader.lineNumber());
            stanza.put("endLine", lines.get(end).lineNumber());
            stanza.put("lines", stanzaLines);
            sink.emit(stanza, indexedHeader.lineNumber(), lines.get(end).lineNumber());
            index = end + 1;
        }
    }

    private List<IndexedLine> configurationLines(NormalizedEvidenceUnit unit) {
        List<String> source = unit.contentLines();
        int firstSeparator = -1;
        for (int index = 0; index < source.size(); index++) {
            if (isSeparator(source.get(index))) {
                firstSeparator = index;
                break;
            }
        }
        int start = firstSeparator < 0 ? 0 : firstSeparator + 1;
        List<IndexedLine> retained = new ArrayList<>();
        for (int index = start; index < source.size(); index++) {
            String line = source.get(index);
            if (line.strip().equals(unit.commandText().strip()) || isSeparator(line)) {
                continue;
            }
            retained.add(new IndexedLine(index + 1, line));
        }
        int last = retained.size() - 1;
        while (last >= 0 && retained.get(last).text().isBlank()) {
            last--;
        }
        if (last >= 0 && TERMINAL_PROMPT.matcher(retained.get(last).text().strip()).matches()) {
            retained.remove(last);
        }
        return List.copyOf(retained);
    }

    private void delimitedSectionCandidates(
            NormalizedEvidenceUnit unit,
            CompiledExtractor compiled,
            CandidateSink sink) {
        for (DelimitedSectionParser.Section section : sectionParser.parse(unit, compiled)) {
            sink.emit(section.value(), section.lineStart(), section.lineEnd());
        }
    }

    private static boolean isSeparator(String line) {
        return CONFIG_SEPARATOR.matcher(line.strip()).matches();
    }

    private Object transform(Object rawValue, List<String> transforms) {
        Object value = rawValue;
        for (String transform : transforms == null ? List.<String>of() : transforms) {
            value = switch (transform) {
                case "trim" -> String.valueOf(value).strip();
                case "lowercase" -> String.valueOf(value).toLowerCase(Locale.ROOT);
                case "integer" -> Long.parseLong(String.valueOf(value).strip());
                case "decimal" -> new BigDecimal(String.valueOf(value).strip());
                case "boolean-enable-disable" -> booleanValue(value);
                case "capacity-to-bytes" -> capacity(value);
                case "duration-to-seconds" -> duration(value);
                case "datetime" -> Instant.parse(String.valueOf(value).strip()).toString();
                default -> throw new IllegalArgumentException("unsupported transform");
            };
        }
        return value;
    }

    private static boolean booleanValue(Object value) {
        return switch (String.valueOf(value).strip().toLowerCase(Locale.ROOT)) {
            case "enable", "enabled", "up", "in use", "true", "yes" -> true;
            case "disable", "disabled", "down", "not used", "false", "no" -> false;
            default -> throw new IllegalArgumentException("boolean value is invalid");
        };
    }

    private static long capacity(Object value) {
        Matcher matcher = CAPACITY.matcher(String.valueOf(value).strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("capacity value is invalid");
        }
        int power = switch (matcher.group(2).toUpperCase(Locale.ROOT)) {
            case "" -> 0;
            case "K" -> 1;
            case "M" -> 2;
            case "G" -> 3;
            case "T" -> 4;
            default -> throw new IllegalArgumentException("capacity unit is invalid");
        };
        return new BigDecimal(matcher.group(1))
                .multiply(BigDecimal.valueOf(1024).pow(power))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static long duration(Object value) {
        Map<String, Long> units = Map.of("week", 604800L, "day", 86400L,
                "hour", 3600L, "minute", 60L, "second", 1L);
        Matcher matcher = DURATION.matcher(String.valueOf(value));
        long total = 0;
        int matches = 0;
        while (matcher.find()) {
            String unit = matcher.group(2).toLowerCase(Locale.ROOT).replaceFirst("s$", "");
            total = Math.addExact(total, Math.multiplyExact(Long.parseLong(matcher.group(1)), units.get(unit)));
            matches++;
        }
        if (matches == 0) {
            throw new IllegalArgumentException("duration value is invalid");
        }
        return total;
    }

    private static List<String> columns(String line) {
        String stripped = line.strip();
        List<String> columns = List.of(stripped.split("\\s{2,}"));
        return columns.size() >= 2 ? columns : List.of(stripped.split("\\s+"));
    }

    private static int tableHeaderIndex(List<String> lines) {
        int bestIndex = -1;
        int bestColumns = 1;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            int columnCount = columns(lines.get(index)).size();
            boolean hasFollowingRow = lines.subList(index + 1, lines.size()).stream()
                    .filter(line -> !line.isBlank() && !line.matches("^[=-]+(?:\\s+[=-]+)*$"))
                    .findFirst()
                    .map(line -> columns(line).size() >= 2)
                    .orElse(false);
            if (hasFollowingRow && columnCount > bestColumns) {
                bestIndex = index;
                bestColumns = columnCount;
            }
        }
        return bestIndex;
    }

    private static Map<String, Object> warning(String ruleId, String code, int lineNumber) {
        return Map.of("ruleId", ruleId, "code", code, "lineNumber", lineNumber);
    }

    /** Legacy keeps its eager ordering; enhanced processes candidates before allocating the next one. */
    private final class CandidateSink {
        private final boolean streaming;
        private final Consumer<Candidate> consumer;
        private final List<SemanticFact> facts;
        private final List<Candidate> buffered;

        private CandidateSink(boolean streaming, Consumer<Candidate> consumer, List<SemanticFact> facts) {
            this.streaming = streaming;
            this.consumer = consumer;
            this.facts = facts;
            this.buffered = streaming ? null : new ArrayList<>();
        }

        private void checkCapacity() {
            if (streaming && facts.size() >= limits.maxFacts()) {
                throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT, "fact count exceeds limit");
            }
        }

        private void emit(Object value, int lineStart, int lineEnd) {
            checkCapacity();
            Candidate candidate = new Candidate(value, lineStart, lineEnd);
            if (streaming) {
                consumer.accept(candidate);
            } else {
                buffered.add(candidate);
            }
        }

        private void finish() {
            if (!streaming) {
                buffered.forEach(consumer);
            }
        }
    }

    private record Candidate(Object rawValue, int lineStart, int lineEnd) {
    }

    private record IndexedLine(int lineNumber, String text) {
    }

    public record ExtractionResult(
            List<SemanticFact> facts,
            List<Map<String, Object>> warnings,
            int redactedCount,
            Set<Integer> redactedCommandIndexes) {
    }

    private static final class SetLike {
        private static final java.util.Set<ObservationStatus> PARSABLE = java.util.Set.of(
                ObservationStatus.OBSERVED, ObservationStatus.PARTIAL, ObservationStatus.NOT_ENABLED);

        private SetLike() {
        }
    }
}
