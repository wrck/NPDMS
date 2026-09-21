package com.dp.deviceops.parser.semantic.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Packs model-aware rule files into the existing rules_json persistence column. */
public final class ParserRuleArtifactCodec {

    private static final String ARTIFACT_VERSION = "1.0.0";
    private final ObjectMapper mapper;

    public ParserRuleArtifactCodec() {
        this(new CanonicalJson().mapper());
    }

    ParserRuleArtifactCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public String encode(ParserReleaseBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        if ("1.0.0".equals(bundle.manifest().engineVersion())) {
            return bundle.rulesJson();
        }
        try {
            return mapper.writeValueAsString(new Artifact(ARTIFACT_VERSION,
                    bundle.modelProfilesJson(), bundle.ruleSetJsonByPath()));
        } catch (JsonProcessingException exception) {
            throw invalid("parser rule artifact cannot be encoded", exception);
        }
    }

    public Decoded decode(ParserReleaseManifest manifest, String storedRulesJson) {
        Objects.requireNonNull(manifest, "manifest");
        if ("1.0.0".equals(manifest.engineVersion())) {
            return new Decoded(requireText(storedRulesJson), null, Map.of());
        }
        try {
            Artifact artifact = mapper.readValue(requireText(storedRulesJson), Artifact.class);
            if (artifact == null || !ARTIFACT_VERSION.equals(artifact.artifactVersion())
                    || artifact.modelProfilesJson() == null || artifact.modelProfilesJson().isBlank()
                    || artifact.ruleSetJsonByPath() == null || artifact.ruleSetJsonByPath().isEmpty()) {
                throw invalid("parser rule artifact is invalid", null);
            }
            return new Decoded(null, artifact.modelProfilesJson(), artifact.ruleSetJsonByPath());
        } catch (JsonProcessingException exception) {
            throw invalid("parser rule artifact is invalid", exception);
        }
    }

    public record Decoded(String rulesJson, String modelProfilesJson, Map<String, String> ruleSetJsonByPath) {
        public Decoded {
            ruleSetJsonByPath = ruleSetJsonByPath == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(ruleSetJsonByPath));
        }
    }

    private record Artifact(
            String artifactVersion,
            String modelProfilesJson,
            Map<String, String> ruleSetJsonByPath) { }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("stored parser rules must not be blank", null);
        }
        return value;
    }

    private static SemanticParserError invalid(String message, Exception cause) {
        return cause == null
                ? new SemanticParserError(SemanticParserError.INVALID_RULES, message)
                : new SemanticParserError(SemanticParserError.INVALID_RULES, message, cause);
    }
}
