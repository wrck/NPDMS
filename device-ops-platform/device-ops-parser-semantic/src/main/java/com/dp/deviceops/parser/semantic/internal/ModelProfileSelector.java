package com.dp.deviceops.parser.semantic.internal;

import com.dp.deviceops.parser.semantic.ModelProfileSelection;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledModelProfile;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

final class ModelProfileSelector {

    private static final String MODEL_KEY = "device.identity.model";
    private static final String CONTEXT_MODEL_KEY = "deviceModel";
    private static final String CONFLICT_WARNING = "MODEL_HINT_CONFLICT";

    Selection select(
            FactExtractor.ExtractionResult discovery,
            Map<String, Object> contextSnapshot,
            CompiledModelProfile genericProfile,
            List<CompiledModelProfile> profiles) {
        Objects.requireNonNull(discovery, "discovery");
        contextSnapshot = contextSnapshot == null ? Map.of() : contextSnapshot;
        Objects.requireNonNull(genericProfile, "genericProfile");
        Objects.requireNonNull(profiles, "profiles");

        SemanticFact logFact = discovery.facts().stream()
                .filter(fact -> MODEL_KEY.equals(fact.semanticKey()))
                .filter(fact -> normalize(fact.value()) != null)
                .sorted(Comparator.comparingDouble(SemanticFact::confidence).reversed()
                        .thenComparingInt(fact -> fact.source().commandIndex())
                        .thenComparingInt(fact -> fact.source().lineStart())
                        .thenComparing(SemanticFact::ruleId))
                .findFirst().orElse(null);
        String logModel = logFact == null ? null : normalize(logFact.value());
        String contextModel = normalize(contextSnapshot.get(CONTEXT_MODEL_KEY));

        String selectedModel;
        ModelProfileSelection.Source source;
        Integer commandIndex = null;
        Integer lineNumber = null;
        List<String> warnings = List.of();
        if (logModel != null) {
            selectedModel = logModel;
            source = ModelProfileSelection.Source.LOG_OUTPUT;
            commandIndex = logFact.source().commandIndex();
            lineNumber = logFact.source().lineStart();
            if (contextModel != null && !logModel.equalsIgnoreCase(contextModel)) {
                warnings = List.of(CONFLICT_WARNING);
            }
        } else if (contextModel != null) {
            selectedModel = contextModel;
            source = ModelProfileSelection.Source.CONTEXT_SNAPSHOT;
        } else {
            return new Selection(new ModelProfileSelection(genericProfile.profileId(), null,
                    ModelProfileSelection.Source.GENERIC, null, null, List.of()), genericProfile);
        }

        CompiledModelProfile profile = profiles.stream()
                .filter(candidate -> matches(candidate, selectedModel))
                .findFirst().orElse(genericProfile);
        return new Selection(new ModelProfileSelection(profile.profileId(), selectedModel, source,
                commandIndex, lineNumber, warnings), profile);
    }

    private static boolean matches(CompiledModelProfile profile, String model) {
        String normalized = model.toLowerCase(Locale.ROOT);
        return profile.exactModels().contains(normalized)
                || profile.modelPrefixes().stream().anyMatch(normalized::startsWith);
    }

    private static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).strip();
        return normalized.isEmpty() ? null : normalized;
    }

    record Selection(ModelProfileSelection metadata, CompiledModelProfile profile) {
        Selection {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(profile, "profile");
        }
    }
}
