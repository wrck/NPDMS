package com.dp.deviceops.parser.semantic;

import com.dp.deviceops.parser.semantic.internal.ImmutableValues;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic aggregate returned by the semantic parser. */
public record SemanticParseResult(
        String schemaVersion,
        String parserVersion,
        String ruleVersion,
        String projectionVersion,
        String inputSha256,
        String ruleSha256,
        String projectionSha256,
        Map<String, Object> snapshot,
        @JsonInclude(JsonInclude.Include.NON_NULL) GenericContent genericContent,
        Map<String, Object> projections,
        @JsonInclude(JsonInclude.Include.NON_NULL) ModelProfileSelection profileSelection,
        Map<String, Object> quality,
        List<BlockObservation> observations,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<NestedBlockObservation> nestedObservations) {

    public SemanticParseResult {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        parserVersion = requireText(parserVersion, "parserVersion");
        ruleVersion = requireText(ruleVersion, "ruleVersion");
        projectionVersion = requireText(projectionVersion, "projectionVersion");
        inputSha256 = requireHash(inputSha256, "inputSha256");
        ruleSha256 = requireHash(ruleSha256, "ruleSha256");
        projectionSha256 = requireHash(projectionSha256, "projectionSha256");
        snapshot = ImmutableValues.deepImmutableMap(snapshot);
        if ("1.0.0".equals(schemaVersion) && genericContent != null) {
            throw new IllegalArgumentException("legacy result must not contain genericContent");
        }
        if ("1.1.0".equals(schemaVersion) && genericContent == null) {
            throw new IllegalArgumentException("structured result requires genericContent");
        }
        projections = ImmutableValues.deepImmutableMap(projections);
        if (!"1.0.0".equals(parserVersion) && profileSelection == null) {
            throw new IllegalArgumentException("model-aware result requires profileSelection");
        }
        if ("1.0.0".equals(parserVersion) && profileSelection != null) {
            throw new IllegalArgumentException("legacy result must not contain profileSelection");
        }
        quality = ImmutableValues.deepImmutableMap(quality);
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        nestedObservations = nestedObservations == null ? List.of() : List.copyOf(nestedObservations);
    }

    public SemanticParseResult(
            String schemaVersion,
            String parserVersion,
            String ruleVersion,
            String projectionVersion,
            String inputSha256,
            String ruleSha256,
            String projectionSha256,
            Map<String, Object> snapshot,
            Map<String, Object> projections,
            ModelProfileSelection profileSelection,
            Map<String, Object> quality,
            List<BlockObservation> observations,
            List<NestedBlockObservation> nestedObservations) {
        this(schemaVersion, parserVersion, ruleVersion, projectionVersion, inputSha256, ruleSha256,
                projectionSha256, snapshot, null, projections, profileSelection, quality, observations,
                nestedObservations);
    }

    public SemanticParseResult(
            String schemaVersion,
            String parserVersion,
            String ruleVersion,
            String projectionVersion,
            String inputSha256,
            String ruleSha256,
            String projectionSha256,
            Map<String, Object> snapshot,
            Map<String, Object> projections,
            ModelProfileSelection profileSelection,
            Map<String, Object> quality,
            List<BlockObservation> observations) {
        this(schemaVersion, parserVersion, ruleVersion, projectionVersion, inputSha256, ruleSha256,
                projectionSha256, snapshot, null, projections, profileSelection, quality, observations, List.of());
    }

    public SemanticParseResult(
            String schemaVersion,
            String parserVersion,
            String ruleVersion,
            String projectionVersion,
            String inputSha256,
            String ruleSha256,
            String projectionSha256,
            Map<String, Object> snapshot,
            Map<String, Object> projections,
            Map<String, Object> quality,
            List<BlockObservation> observations) {
        this(schemaVersion, parserVersion, ruleVersion, projectionVersion, inputSha256, ruleSha256,
                projectionSha256, snapshot, null, projections, null, quality, observations, List.of());
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    private static String requireHash(String value, String name) {
        String hash = requireText(value, name);
        if (!hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256");
        }
        return hash;
    }
}
