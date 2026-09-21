package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.BlockObservation;
import com.dp.deviceops.parser.semantic.GenericContent;
import com.dp.deviceops.parser.semantic.ObservationStatus;
import com.dp.deviceops.parser.semantic.NestedBlockObservation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QualityReporter {

    public Map<String, Object> report(
            List<NormalizedEvidenceUnit> units,
            List<BlockObservation> observations,
            List<NestedBlockObservation> nestedObservations,
            FactExtractor.ExtractionResult extraction,
            GenericContent genericContent,
            int evidenceRedactedValueCount) {
        Map<ObservationStatus, Long> counts = new EnumMap<>(ObservationStatus.class);
        observations.forEach(observation -> counts.merge(observation.status(), 1L, Long::sum));
        long mapped = observations.stream()
                .filter(observation -> observation.blockRole() != null && !observation.blockRole().isBlank())
                .count();
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (ObservationStatus status : ObservationStatus.values()) {
            statusCounts.put(status.name(), counts.getOrDefault(status, 0L));
        }
        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("blockCount", units.size());
        quality.put("mappedBlockCount", mapped);
        quality.put("blockCoverage", units.isEmpty() ? 1.0 : (double) mapped / units.size());
        quality.put("factCount", extraction.facts().size());
        quality.put("statusCounts", statusCounts);
        quality.put("emptyBlockCount", counts.getOrDefault(ObservationStatus.NO_DATA, 0L));
        quality.put("failedBlockCount", counts.getOrDefault(ObservationStatus.EXECUTION_FAILED, 0L));
        quality.put("truncatedBlockCount", units.stream().filter(NormalizedEvidenceUnit::truncated).count());
        quality.put("corruptedBlockCount", counts.getOrDefault(ObservationStatus.SOURCE_CORRUPTED, 0L));
        quality.put("unparsedBlockCount", counts.getOrDefault(ObservationStatus.UNPARSED, 0L));
        if (!nestedObservations.isEmpty()) {
            long nestedMapped = nestedObservations.stream()
                    .filter(observation -> observation.blockRole() != null
                            && !observation.blockRole().isBlank())
                    .count();
            quality.put("nestedBlockCount", nestedObservations.size());
            quality.put("nestedMappedBlockCount", nestedMapped);
            quality.put("nestedUnparsedBlockCount", nestedObservations.stream()
                    .filter(observation -> observation.status() == ObservationStatus.UNPARSED)
                    .count());
        }
        quality.put("redactedValueCount", Math.addExact(
                extraction.redactedCount(), evidenceRedactedValueCount));
        int observationWarnings = observations.stream().mapToInt(item -> item.warnings().size()).sum();
        int genericWarnings = genericContent == null ? 0 : genericContent.units().stream()
                .mapToInt(unit -> unit.warnings().size()).sum();
        if (genericContent != null) {
            reportGeneric(quality, genericContent, genericWarnings);
        }
        quality.put("warningCount", extraction.warnings().size() + observationWarnings + genericWarnings);
        quality.put("warnings", extraction.warnings());
        return quality;
    }

    private static void reportGeneric(
            Map<String, Object> quality,
            GenericContent genericContent,
            int genericWarnings) {
        List<String> types = List.of(
                "keyValue", "keyValueTree", "table", "recordList", "configStanza", "list", "text");
        Map<String, Long> typeCounts = new LinkedHashMap<>();
        for (String type : types) {
            long count = genericContent.units().stream().flatMap(unit -> unit.sections().stream())
                    .filter(section -> type.equals(section.type())).count();
            typeCounts.put(type, count);
        }
        quality.put("genericUnitCount", genericContent.units().size());
        quality.put("genericSectionCount", genericContent.units().stream()
                .mapToInt(unit -> unit.sections().size()).sum());
        quality.put("genericTypeCounts", typeCounts);
        quality.put("genericPartialUnitCount", countStatus(genericContent, GenericContent.StructureStatus.PARTIAL));
        quality.put("genericTextOnlyUnitCount", countStatus(
                genericContent, GenericContent.StructureStatus.TEXT_ONLY));
        quality.put("genericEmptyUnitCount", countStatus(genericContent, GenericContent.StructureStatus.EMPTY));
        quality.put("genericLimitedUnitCount", countStatus(genericContent, GenericContent.StructureStatus.LIMITED));
        quality.put("genericWarningCount", genericWarnings);
    }

    private static long countStatus(GenericContent content, GenericContent.StructureStatus status) {
        return content.units().stream().filter(unit -> unit.structureStatus() == status).count();
    }
}
