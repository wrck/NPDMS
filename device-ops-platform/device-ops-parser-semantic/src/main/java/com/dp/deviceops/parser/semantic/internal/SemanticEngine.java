package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.BlockObservation;
import com.dp.deviceops.parser.semantic.GenericContent;
import com.dp.deviceops.parser.semantic.ObservationStatus;
import com.dp.deviceops.parser.semantic.NestedBlockObservation;
import com.dp.deviceops.parser.semantic.SemanticParseResult;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.evidence.EvidenceDocument;
import com.dp.deviceops.parser.semantic.evidence.EvidenceUnit;
import com.dp.deviceops.parser.semantic.plan.ParserExtension;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class SemanticEngine {

    private final SemanticLimits limits;
    private final CanonicalJson canonicalJson;
    private final EvidenceNormalizer normalizer;
    private final TerminalControlSanitizer terminalSanitizer;
    private final SensitiveEvidenceSanitizer sensitiveSanitizer;
    private final RuleEngine ruleEngine;
    private final FactExtractor factExtractor;
    private final SnapshotAssembler snapshotAssembler;
    private final ProjectionEngine projectionEngine;
    private final QualityReporter qualityReporter;
    private final SemanticResultFactory resultFactory;
    private final ModelProfileSelector modelProfileSelector;
    private final NestedEvidenceExpander nestedEvidenceExpander;
    private final GenericStructureParser genericStructureParser;

    public SemanticEngine(SemanticLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.canonicalJson = new CanonicalJson();
        this.normalizer = new EvidenceNormalizer();
        this.terminalSanitizer = new TerminalControlSanitizer();
        SensitiveValueRedactor sensitiveValueRedactor = new SensitiveValueRedactor();
        this.sensitiveSanitizer = new SensitiveEvidenceSanitizer(sensitiveValueRedactor);
        this.ruleEngine = new RuleEngine();
        this.factExtractor = new FactExtractor(sensitiveValueRedactor, limits);
        this.snapshotAssembler = new SnapshotAssembler();
        this.projectionEngine = new ProjectionEngine();
        this.qualityReporter = new QualityReporter();
        this.resultFactory = new SemanticResultFactory(canonicalJson);
        this.modelProfileSelector = new ModelProfileSelector();
        this.nestedEvidenceExpander = new NestedEvidenceExpander(limits);
        this.genericStructureParser = new GenericStructureParser(limits);
    }

    public SemanticParseResult execute(EvidenceDocument document, ParserPlan plan) {
        Objects.requireNonNull(plan, "plan");
        List<NormalizedEvidenceUnit> normalized = normalizer.normalize(document);
        enforceInputLimits(normalized);
        TerminalControlSanitizer.Result sanitized = terminalSanitizer.sanitize(normalized);
        List<NormalizedEvidenceUnit> identityUnits = sanitized.units();
        boolean enhanced = ParserPlanCompiler.ENHANCED_ENGINE_VERSION.equals(plan.coordinate().engineVersion());
        boolean structured = enhanced || ParserPlanCompiler.STRUCTURED_ENGINE_VERSION.equals(
                plan.coordinate().engineVersion());
        SensitiveValueRedactor redactor = enhanced ? new SensitiveValueRedactor(true) : null;
        SensitiveEvidenceSanitizer sensitiveSanitizer = enhanced
                ? new SensitiveEvidenceSanitizer(redactor, true) : this.sensitiveSanitizer;
        FactExtractor factExtractor = enhanced ? new FactExtractor(redactor, limits, true) : this.factExtractor;
        NestedEvidenceExpander nestedEvidenceExpander = enhanced
                ? new NestedEvidenceExpander(limits, true) : this.nestedEvidenceExpander;
        GenericStructureParser genericStructureParser = enhanced
                ? new GenericStructureParser(limits, true) : this.genericStructureParser;
        SensitiveEvidenceSanitizer.Result sensitive = structured
                ? sensitiveSanitizer.sanitize(identityUnits)
                : new SensitiveEvidenceSanitizer.Result(identityUnits, 0, Set.of());
        List<NormalizedEvidenceUnit> workingUnits = sensitive.units();
        boolean legacy = ParserPlanCompiler.LEGACY_ENGINE_VERSION.equals(plan.coordinate().engineVersion());
        ModelProfileSelector.Selection selection = null;
        List<ParserPlan.CompiledRule> effectiveRules = plan.rules();
        if (!legacy) {
            List<BlockObservation> discoveryObservations = ruleEngine.observe(
                    workingUnits, plan.discoveryRules(), sanitized.changedUnitIndexes());
            FactExtractor.ExtractionResult discovery = factExtractor.extract(
                    workingUnits, discoveryObservations, plan.discoveryRules());
            selection = modelProfileSelector.select(discovery, document.contextSnapshot(),
                    plan.genericProfile(), plan.modelProfiles());
            effectiveRules = new ArrayList<>(plan.rules());
            effectiveRules.addAll(selection.profile().rules());
            effectiveRules = List.copyOf(effectiveRules);
        }
        List<ParserPlan.CompiledStructureRule> effectiveStructureRules = new ArrayList<>(
                plan.structureRules());
        if (selection != null) {
            effectiveStructureRules.addAll(selection.profile().structureRules());
        }
        effectiveStructureRules = List.copyOf(effectiveStructureRules);
        List<BlockObservation> observations = ruleEngine.observe(workingUnits, effectiveRules,
                sanitized.changedUnitIndexes());
        NestedEvidenceExpander.Expansion expansion = nestedEvidenceExpander.expand(
                workingUnits, observations, effectiveRules, structured);
        GenericStructureParser.Result structures = structured
                ? genericStructureParser.parse(workingUnits, expansion, effectiveStructureRules, effectiveRules)
                : GenericStructureParser.Result.empty();
        FactExtractor.ExtractionResult extraction = factExtractor.extract(
                workingUnits, observations, effectiveRules, structures);
        List<NestedBlockObservation> nestedObservations = List.of();
        if (!expansion.units().isEmpty()) {
            List<NormalizedEvidenceUnit> allUnits = new ArrayList<>(workingUnits);
            allUnits.addAll(expansion.units());
            enforceInputLimits(allUnits);
            List<BlockObservation> nestedInternalObservations = ruleEngine.observe(
                    expansion.units(), effectiveRules, Set.of());
            FactExtractor.ExtractionResult nestedExtraction = factExtractor.extract(
                    expansion.units(), nestedInternalObservations, effectiveRules, structures);
            extraction = merge(extraction, nestedExtraction);
            nestedObservations = nestedObservations(
                    expansion, nestedInternalObservations);
        }
        if (plan.extension() != null) {
            EvidenceDocument extensionDocument = structured
                    ? evidenceDocument(workingUnits, document.contextSnapshot()) : document;
            extraction = merge(extraction, plan.extension().extract(extensionDocument,
                    new ParserExtension.ExtensionContext(plan.coordinate())));
        }
        observations = markFullyRedacted(observations, extraction);
        Map<String, Object> snapshot = snapshotAssembler.assemble(observations, extraction);
        Map<String, Object> projections = projectionEngine.project(
                snapshot, plan.catalogs().projectionCatalog().profiles());
        Map<String, Object> quality = qualityReporter.report(
                workingUnits, observations, nestedObservations, extraction,
                structured ? structures.content() : null, sensitive.redactedValueCount());
        SemanticParseResult result = resultFactory.create(identityUnits, plan.catalogs(), snapshot,
                structured ? structures.content() : null, projections, quality, observations,
                nestedObservations, plan.coordinate().engineVersion(),
                document.contextSnapshot(), selection == null ? null : selection.metadata());
        if (canonicalJson.bytes(result).length > limits.maxResultBytes()) {
            throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT,
                    "result size exceeds limit");
        }
        return result;
    }

    private static EvidenceDocument evidenceDocument(
            List<NormalizedEvidenceUnit> units,
            Map<String, Object> contextSnapshot) {
        List<EvidenceUnit> safeUnits = units.stream().map(unit -> new EvidenceUnit(
                unit.unitIndex(), unit.unitType(), unit.attributes(), unit.contentLines(),
                unit.errorLines(), unit.truncated())).toList();
        return new EvidenceDocument(safeUnits, contextSnapshot);
    }

    private FactExtractor.ExtractionResult merge(
            FactExtractor.ExtractionResult topLevel,
            FactExtractor.ExtractionResult nested) {
        List<SemanticFact> facts = new ArrayList<>(topLevel.facts());
        facts.addAll(nested.facts());
        if (facts.size() > limits.maxFacts()) {
            throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT,
                    "fact count exceeds limit");
        }
        Comparator<Map<String, Object>> warningOrder = Comparator
                .<Map<String, Object>, String>comparing(
                        warning -> String.valueOf(warning.get("ruleId")))
                .thenComparing(warning -> String.valueOf(warning.get("code")))
                .thenComparingInt(warning -> ((Number) warning.getOrDefault("lineNumber", 0)).intValue());
        List<Map<String, Object>> warnings = new ArrayList<>(topLevel.warnings());
        warnings.addAll(nested.warnings());
        warnings.sort(warningOrder);
        Set<Integer> redacted = new java.util.LinkedHashSet<>(topLevel.redactedCommandIndexes());
        redacted.addAll(nested.redactedCommandIndexes());
        return new FactExtractor.ExtractionResult(List.copyOf(facts), List.copyOf(warnings),
                Math.addExact(topLevel.redactedCount(), nested.redactedCount()), Set.copyOf(redacted));
    }

    private static List<NestedBlockObservation> nestedObservations(
            NestedEvidenceExpander.Expansion expansion,
            List<BlockObservation> observations) {
        Map<Integer, NormalizedEvidenceUnit> unitsByIndex = expansion.units().stream()
                .collect(java.util.stream.Collectors.toMap(
                        NormalizedEvidenceUnit::unitIndex, java.util.function.Function.identity()));
        return observations.stream().map(observation -> {
            NestedEvidenceExpander.NestedIdentity identity =
                    expansion.identities().get(observation.commandIndex());
            NormalizedEvidenceUnit unit = unitsByIndex.get(observation.commandIndex());
            return new NestedBlockObservation(identity.parentCommandIndex(), identity.sectionIndex(), 1,
                    unit.provenance().nestedCommandText(), observation.blockRole(), observation.status(),
                    observation.confidence(), identity.lineStart(), identity.lineEnd(),
                    observation.matchedRuleIds(), observation.warnings());
        }).toList();
    }

    private FactExtractor.ExtractionResult merge(
            FactExtractor.ExtractionResult base,
            ParserExtension.ExtensionResult extension) {
        Objects.requireNonNull(extension, "extension result");
        List<SemanticFact> facts = new ArrayList<>(base.facts());
        for (ParserExtension.ExtensionFact fact : extension.facts()) {
            ParserExtension.ExtensionEvidence source = fact.source();
            facts.add(new SemanticFact(fact.semanticKey(), fact.value(), fact.dataType(), fact.unit(),
                    fact.status(), fact.confidence(), fact.cardinality(), fact.conflictPolicy(),
                    new SemanticFact.SourceEvidence(source.unitIndex(), source.commandText(),
                            source.lineStart(), source.lineEnd()), fact.ruleId()));
            if (facts.size() > limits.maxFacts()) {
                throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT,
                        "fact count exceeds limit");
            }
        }
        List<Map<String, Object>> warnings = new ArrayList<>(base.warnings());
        extension.warnings().forEach(warning -> warnings.add(Map.of(
                "ruleId", warning.ruleId(),
                "code", warning.code(),
                "lineNumber", warning.lineNumber())));
        return new FactExtractor.ExtractionResult(List.copyOf(facts), List.copyOf(warnings),
                base.redactedCount(), base.redactedCommandIndexes());
    }

    private static List<BlockObservation> markFullyRedacted(
            List<BlockObservation> observations,
            FactExtractor.ExtractionResult extraction) {
        Set<Integer> factUnits = extraction.facts().stream()
                .filter(fact -> fact.source().nestingDepthOrZero() == 0)
                .map(fact -> fact.source().commandIndex())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return observations.stream().map(observation -> {
            if (!extraction.redactedCommandIndexes().contains(observation.commandIndex())
                    || factUnits.contains(observation.commandIndex())) {
                return observation;
            }
            return new BlockObservation(observation.commandIndex(), observation.blockRole(),
                    ObservationStatus.REDACTED, observation.confidence(), observation.sourceLineStart(),
                    observation.sourceLineEnd(), observation.matchedRuleIds(), observation.warnings());
        }).toList();
    }

    private void enforceInputLimits(List<NormalizedEvidenceUnit> units) {
        if (units.size() > limits.maxCommandBlocks()) {
            throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT,
                    "command block count exceeds limit");
        }
        long total = 0;
        for (NormalizedEvidenceUnit unit : units) {
            long size = "COMMAND_OUTPUT".equals(unit.unitType())
                    ? utf8Bytes(unit.commandText())
                    : utf8Bytes(unit.unitType()) + unit.attributes().entrySet().stream()
                            .mapToLong(entry -> utf8Bytes(entry.getKey()) + utf8Bytes(entry.getValue())).sum();
            size += unit.contentLines().stream().mapToLong(SemanticEngine::utf8Bytes).sum();
            size += unit.errorLines().stream().mapToLong(SemanticEngine::utf8Bytes).sum();
            if (size > limits.maxBlockBytes()) {
                throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT,
                        "command block size exceeds limit");
            }
            total = Math.addExact(total, size);
            if (total > limits.maxTotalInputBytes()) {
                throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT,
                        "total input size exceeds limit");
            }
        }
    }

    private static long utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
