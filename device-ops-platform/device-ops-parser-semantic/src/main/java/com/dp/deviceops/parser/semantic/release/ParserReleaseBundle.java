package com.dp.deviceops.parser.semantic.release;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ParserReleaseBundle(
        ParserReleaseManifest manifest,
        String rulesJson,
        String projectionsJson,
        String modelProfilesJson,
        Map<String, String> ruleSetJsonByPath,
        List<VerificationCase> verificationCases) {

    public ParserReleaseBundle {
        Objects.requireNonNull(manifest, "manifest");
        projectionsJson = requireText(projectionsJson, "projectionsJson");
        ruleSetJsonByPath = ruleSetJsonByPath == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(ruleSetJsonByPath));
        if ("1.0.0".equals(manifest.engineVersion())) {
            rulesJson = requireText(rulesJson, "rulesJson");
            if (modelProfilesJson != null || !ruleSetJsonByPath.isEmpty()) {
                throw new IllegalArgumentException("legacy release must not contain model profiles");
            }
        } else {
            modelProfilesJson = requireText(modelProfilesJson, "modelProfilesJson");
            if (rulesJson != null || ruleSetJsonByPath.isEmpty()) {
                throw new IllegalArgumentException("model-aware release requires rule sets");
            }
            ruleSetJsonByPath.forEach((path, json) -> {
                requireText(path, "rule set path");
                requireText(json, "rule set JSON");
            });
        }
        verificationCases = List.copyOf(Objects.requireNonNull(verificationCases, "verificationCases"));
        if (verificationCases.isEmpty()) {
            throw new IllegalArgumentException("verificationCases must not be empty");
        }
    }

    public ParserReleaseBundle(ParserReleaseManifest manifest, String rulesJson,
            String projectionsJson, List<VerificationCase> verificationCases) {
        this(manifest, rulesJson, projectionsJson, null, Map.of(), verificationCases);
    }

    public record VerificationCase(String caseId, String inputContent, String expectedResultJson) {
        public VerificationCase {
            caseId = requireText(caseId, "caseId");
            inputContent = Objects.requireNonNull(inputContent, "inputContent");
            Objects.requireNonNull(expectedResultJson, "expectedResultJson");
            if (expectedResultJson.isBlank()) {
                throw new IllegalArgumentException("expectedResultJson is required");
            }
        }
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return text;
    }
}
