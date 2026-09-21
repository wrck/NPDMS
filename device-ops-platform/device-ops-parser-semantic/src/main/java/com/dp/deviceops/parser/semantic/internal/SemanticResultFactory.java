package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.BlockObservation;
import com.dp.deviceops.parser.semantic.GenericContent;
import com.dp.deviceops.parser.semantic.ModelProfileSelection;
import com.dp.deviceops.parser.semantic.NestedBlockObservation;
import com.dp.deviceops.parser.semantic.SemanticParseResult;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.dp.deviceops.core.model.CommandBlockStatus;

import java.util.List;
import java.util.Map;

public final class SemanticResultFactory {

    private final CanonicalJson canonicalJson;

    public SemanticResultFactory(CanonicalJson canonicalJson) {
        this.canonicalJson = canonicalJson;
    }

    public SemanticParseResult create(
            List<NormalizedEvidenceUnit> units,
            SemanticCatalog catalogs,
            Map<String, Object> snapshot,
            GenericContent genericContent,
            Map<String, Object> projections,
            Map<String, Object> quality,
            List<BlockObservation> observations,
            List<NestedBlockObservation> nestedObservations,
            String engineVersion,
            Map<String, Object> contextSnapshot,
            ModelProfileSelection profileSelection) {
        boolean modelAware = !"1.0.0".equals(engineVersion);
        return new SemanticParseResult(
                genericContent == null ? "1.0.0" : "1.1.0",
                engineVersion,
                catalogs.ruleCatalog().catalogVersion(),
                catalogs.projectionCatalog().catalogVersion(),
                canonicalJson.sha256(modelAware
                        ? new ModelAwareInputHashMaterial(inputHashMaterial(units), contextSnapshot)
                        : inputHashMaterial(units)),
                canonicalJson.sha256(catalogs.ruleCatalog()),
                canonicalJson.sha256(catalogs.projectionCatalog()),
                snapshot,
                genericContent,
                projections,
                profileSelection,
                quality,
                observations,
                nestedObservations);
    }

    private static Object inputHashMaterial(List<NormalizedEvidenceUnit> units) {
        if (units.stream().allMatch(unit -> "COMMAND_OUTPUT".equals(unit.unitType()))) {
            return units.stream().map(unit -> new CommandHashMaterial(
                    unit.unitIndex(), unit.commandText(), unit.status(), unit.contentLines(),
                    unit.errorLines(), unit.receivedBytes(), unit.pageCount(), unit.truncated(),
                    unit.exitCode())).toList();
        }
        return units;
    }

    private record CommandHashMaterial(
            int commandIndex,
            String commandText,
            CommandBlockStatus status,
            List<String> stdoutLines,
            List<String> stderrLines,
            @JsonIgnore long receivedBytes,
            int pageCount,
            boolean truncated,
            Integer exitCode) {
    }

    private record ModelAwareInputHashMaterial(
            Object commandEvidence,
            Map<String, Object> contextSnapshot) {
    }
}
