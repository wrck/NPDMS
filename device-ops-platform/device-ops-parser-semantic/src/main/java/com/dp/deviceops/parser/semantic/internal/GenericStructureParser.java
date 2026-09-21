package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.GenericContent;
import com.dp.deviceops.parser.semantic.GenericContent.Section;
import com.dp.deviceops.parser.semantic.GenericContent.StructureStatus;
import com.dp.deviceops.parser.semantic.GenericContent.Warning;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledRule;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledStructureRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

final class GenericStructureParser {

    private static final Pattern PROMPT = Pattern.compile("^\\S+[>#]\\s*$");

    private final SemanticLimits limits;
    private final SelectorMatcher selectorMatcher = new SelectorMatcher();
    private final StructuralSegmenter segmenter;
    private final boolean enhanced;

    GenericStructureParser(SemanticLimits limits) {
        this(limits, false);
    }

    GenericStructureParser(SemanticLimits limits, boolean enhanced) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.enhanced = enhanced;
        this.segmenter = new StructuralSegmenter(enhanced);
    }

    Result parse(
            List<NormalizedEvidenceUnit> topLevelUnits,
            NestedEvidenceExpander.Expansion expansion,
            List<CompiledStructureRule> structureRules,
            List<CompiledRule> semanticRules) {
        List<GenericContent.Unit> units = new ArrayList<>();
        Map<UnitKey, List<Section>> sectionsByUnit = new LinkedHashMap<>();
        Map<Integer, List<NestedEvidenceExpander.SectionSeed>> seedsByParent = new LinkedHashMap<>();
        expansion.sectionSeeds().stream()
                .sorted(Comparator.comparingInt(NestedEvidenceExpander.SectionSeed::parentCommandIndex)
                        .thenComparingInt(NestedEvidenceExpander.SectionSeed::sectionIndex))
                .forEach(seed -> seedsByParent.computeIfAbsent(seed.parentCommandIndex(), ignored -> new ArrayList<>())
                        .add(seed));

        topLevelUnits.stream().sorted(Comparator.comparingInt(NormalizedEvidenceUnit::unitIndex))
                .forEach(unit -> {
                    ParsedUnit parsed = parseUnit(unit.provenance().commandIndex(), null, null, 0,
                            unit.commandText(), numbered(unit.contentLines(), unit.provenance().lineOffset()),
                            unit.truncated(), structureRules, semanticRules);
                    units.add(parsed.unit());
                    sectionsByUnit.put(new UnitKey(unit.provenance().commandIndex(), null), parsed.unit().sections());
                    for (NestedEvidenceExpander.SectionSeed seed
                            : seedsByParent.getOrDefault(unit.provenance().commandIndex(), List.of())) {
                        ParsedUnit nested = parseUnit(seed.parentCommandIndex(), seed.parentCommandIndex(),
                                seed.sectionIndex(), 1, seed.commandText(),
                                nestedLines(seed), seed.truncated(),
                                structureRules, semanticRules);
                        units.add(nested.unit());
                        sectionsByUnit.put(new UnitKey(seed.parentCommandIndex(), seed.sectionIndex()),
                                nested.unit().sections());
                    }
                });
        return new Result(new GenericContent(units), sectionsByUnit);
    }

    private ParsedUnit parseUnit(
            int commandIndex,
            Integer parentCommandIndex,
            Integer sectionIndex,
            int nestingDepth,
            String commandText,
            List<StructureLine> sourceLines,
            boolean truncated,
            List<CompiledStructureRule> structureRules,
            List<CompiledRule> semanticRules) {
        int sourceStart = sourceLines.isEmpty() ? 1 : sourceLines.getFirst().lineNumber();
        int sourceEnd = sourceLines.isEmpty() ? sourceStart : sourceLines.getLast().lineNumber();
        List<StructureLine> lines = clean(sourceLines, commandText);
        StructureBudget budget = new StructureBudget(limits, enhanced);
        StructuralSegmenter.Directive directive = directive(commandText, lines, structureRules,
                semanticRules);
        StructuralSegmenter.Result segmented = segmenter.segment(lines, directive, budget);
        List<Warning> warnings = new ArrayList<>(segmented.warnings());
        segmented.sections().forEach(section -> warnings.addAll(section.warnings()));
        if (truncated && !lines.isEmpty()) {
            warnings.add(new Warning("SOURCE_TRUNCATED", lines.getLast().lineNumber()));
        }
        warnings.sort(Comparator.comparingInt(Warning::lineNumber).thenComparing(Warning::code));
        StructureStatus status = status(lines, segmented.sections(), warnings, budget.limited());
        if (enhanced && status != StructureStatus.LIMITED && status != StructureStatus.EMPTY
                && segmented.sections().stream().anyMatch(GenericStructureParser::hasUnparsedContent)) {
            status = segmented.sections().stream().allMatch(section -> "text".equals(section.type()))
                    && warnings.isEmpty() ? StructureStatus.TEXT_ONLY : StructureStatus.PARTIAL;
        }
        if (enhanced && (containsDepthLimit(segmented.sections())
                || warnings.stream().anyMatch(warning -> isDepthLimit(warning.code())))) {
            status = StructureStatus.LIMITED;
        }
        GenericContent.Unit unit = new GenericContent.Unit(commandIndex, parentCommandIndex, sectionIndex,
                nestingDepth, commandText, sourceStart, sourceEnd, status, segmented.sections(), warnings,
                budget.omittedLineCount() == 0 ? null : budget.omittedLineCount());
        return new ParsedUnit(unit);
    }

    private StructuralSegmenter.Directive directive(
            String commandText,
            List<StructureLine> lines,
            List<CompiledStructureRule> structureRules,
            List<CompiledRule> semanticRules) {
        List<String> content = lines.stream().map(StructureLine::text).toList();
        Optional<CompiledStructureRule> matched = structureRules.stream()
                .filter(rule -> rule.selectors().stream()
                        .anyMatch(selector -> selectorMatcher.matches(selector, commandText, content)))
                .findFirst();
        boolean configEnabled = semanticRules.stream()
                .filter(rule -> rule.selectors().stream()
                        .anyMatch(selector -> selectorMatcher.matches(selector, commandText, content)))
                .flatMap(rule -> rule.extractors().stream())
                .anyMatch(extractor -> "CONFIG_STANZA".equals(extractor.source().type()));
        if (matched.isEmpty()) {
            return new StructuralSegmenter.Directive("AUTO", null, null, List.of(), List.of("!"),
                    configEnabled);
        }
        CompiledStructureRule rule = matched.get();
        return new StructuralSegmenter.Directive(rule.source().mode(), rule.source().type(),
                rule.recordStartPattern(), rule.columnNames(), rule.configSeparators(), configEnabled);
    }

    private static List<StructureLine> numbered(List<String> lines, int lineOffset) {
        List<StructureLine> result = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            result.add(new StructureLine(Math.addExact(lineOffset, index + 1), lines.get(index)));
        }
        return List.copyOf(result);
    }

    private static List<StructureLine> nestedLines(NestedEvidenceExpander.SectionSeed seed) {
        List<StructureLine> lines = numbered(seed.lines(), seed.lineStart() - 1);
        return lines.isEmpty() ? lines : List.copyOf(lines.subList(1, lines.size()));
    }

    private static List<StructureLine> clean(List<StructureLine> lines, String commandText) {
        int start = 0;
        int end = lines.size();
        while (start < end && lines.get(start).text().isBlank()) {
            start++;
        }
        if (start < end && lines.get(start).text().strip().equals(commandText.strip())) {
            start++;
        }
        while (start < end && lines.get(start).text().isBlank()) {
            start++;
        }
        while (end > start && lines.get(end - 1).text().isBlank()) {
            end--;
        }
        if (end > start && PROMPT.matcher(lines.get(end - 1).text().strip()).matches()) {
            end--;
        }
        while (end > start && lines.get(end - 1).text().isBlank()) {
            end--;
        }
        return List.copyOf(lines.subList(start, end));
    }

    private static StructureStatus status(
            List<StructureLine> lines,
            List<Section> sections,
            List<Warning> warnings,
            boolean limited) {
        if (limited) {
            return StructureStatus.LIMITED;
        }
        if (lines.isEmpty()) {
            return StructureStatus.EMPTY;
        }
        boolean hasText = sections.stream().anyMatch(section -> "text".equals(section.type()));
        boolean hasStructure = sections.stream().anyMatch(section -> !"text".equals(section.type()));
        if (!hasStructure) {
            return warnings.isEmpty() ? StructureStatus.TEXT_ONLY : StructureStatus.PARTIAL;
        }
        return hasText || !warnings.isEmpty() ? StructureStatus.PARTIAL : StructureStatus.STRUCTURED;
    }

    private static boolean hasUnparsedContent(Section section) {
        if ("text".equals(section.type()) || !section.warnings().isEmpty()) {
            return true;
        }
        if (section.fields().get("unparsedLines") instanceof List<?> unparsed && !unparsed.isEmpty()) {
            return true;
        }
        if (section.fields().get("records") instanceof List<?> records) {
            return records.stream().map(GenericContent.RecordValue.class::cast).anyMatch(record ->
                    (record.entries().isEmpty() && record.sections().isEmpty())
                            || record.sections().stream().anyMatch(GenericStructureParser::hasUnparsedContent));
        }
        return false;
    }

    private static boolean isDepthLimit(String code) {
        return code.contains("DEPTH_LIMIT") || "MAX_DEPTH_EXCEEDED".equals(code);
    }

    private static boolean containsDepthLimit(List<Section> sections) {
        for (Section section : sections) {
            if (section.warnings().stream().anyMatch(warning -> isDepthLimit(warning.code()))) {
                return true;
            }
            if (section.fields().get("records") instanceof List<?> records
                    && records.stream().map(GenericContent.RecordValue.class::cast)
                    .anyMatch(record -> containsDepthLimit(record.sections()))) {
                return true;
            }
        }
        return false;
    }

    record UnitKey(int parentCommandIndex, Integer sectionIndex) {
    }

    record Result(GenericContent content, Map<UnitKey, List<Section>> sectionsByUnit) {
        Result {
            content = Objects.requireNonNull(content, "content");
            Map<UnitKey, List<Section>> copy = new LinkedHashMap<>();
            sectionsByUnit.forEach((key, value) -> copy.put(key, List.copyOf(value)));
            sectionsByUnit = Collections.unmodifiableMap(copy);
        }

        List<Section> sectionsFor(NormalizedEvidenceUnit unit) {
            Integer sectionIndex = unit.provenance().nestingDepth() == 0
                    ? null : unit.provenance().sectionIndex();
            return sectionsByUnit.getOrDefault(
                    new UnitKey(unit.provenance().commandIndex(), sectionIndex), List.of());
        }

        static Result empty() {
            return new Result(new GenericContent(List.of()), Map.of());
        }
    }

    private record ParsedUnit(GenericContent.Unit unit) {
    }
}
