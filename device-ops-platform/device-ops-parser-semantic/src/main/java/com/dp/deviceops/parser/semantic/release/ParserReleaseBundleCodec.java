package com.dp.deviceops.parser.semantic.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class ParserReleaseBundleCodec {

    private static final String MANIFEST_VERSION = "1.0.0";
    private static final List<String> COMMON_FILES = List.of(
            "manifest.json", "projections.json", "verification-cases.json");

    private final ObjectMapper objectMapper;

    public ParserReleaseBundleCodec() {
        this(new CanonicalJson().mapper());
    }

    ParserReleaseBundleCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public ParserReleaseBundle decode(Path releaseDirectory) throws IOException {
        Path root = Objects.requireNonNull(releaseDirectory, "releaseDirectory")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw invalid("parser release directory does not exist");
        }
        for (String requiredFile : COMMON_FILES) {
            if (!Files.isRegularFile(resolveChild(root, requiredFile))) {
                throw invalid("parser release is missing required files");
            }
        }
        try {
            ParserReleaseManifest manifest = objectMapper.readValue(
                    resolveChild(root, "manifest.json").toFile(), ParserReleaseManifest.class);
            if (!MANIFEST_VERSION.equals(manifest.manifestVersion())) {
                throw invalid("parser release manifest version is unsupported");
            }
            boolean modelAware = !ParserPlanCompiler.LEGACY_ENGINE_VERSION.equals(
                    manifest.engineVersion());
            String engineFile = modelAware ? "model-profiles.json" : "rules.json";
            if (!Files.isRegularFile(resolveChild(root, engineFile))) {
                throw invalid("parser release is missing required files");
            }
            VerificationDescriptor descriptor = objectMapper.readValue(
                    resolveChild(root, "verification-cases.json").toFile(), VerificationDescriptor.class);
            if (descriptor == null || descriptor.cases() == null || descriptor.cases().isEmpty()) {
                throw invalid("parser release verification cases are required");
            }
            List<ParserReleaseBundle.VerificationCase> cases = descriptor.cases().stream()
                    .map(item -> decodeCase(root, item)).toList();
            String profilesJson = modelAware
                    ? Files.readString(resolveChild(root, "model-profiles.json"), StandardCharsets.UTF_8)
                    : null;
            Map<String, String> ruleSets = modelAware ? readRuleSets(root, profilesJson) : Map.of();
            return new ParserReleaseBundle(manifest,
                    modelAware ? null : Files.readString(resolveChild(root, "rules.json"), StandardCharsets.UTF_8),
                    Files.readString(resolveChild(root, "projections.json"), StandardCharsets.UTF_8),
                    profilesJson,
                    ruleSets,
                    cases);
        } catch (JsonProcessingException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                    "parser release JSON is invalid", exception);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                    "parser release fields are invalid", exception);
        }
    }

    private Map<String, String> readRuleSets(Path root, String profilesJson) throws IOException {
        ModelProfileCatalog profiles = objectMapper.readValue(profilesJson, ModelProfileCatalog.class);
        Map<String, String> loaded = new TreeMap<>();
        List<String> paths = new java.util.ArrayList<>(profiles.baseRuleSets());
        paths.addAll(profiles.genericProfile().ruleSets());
        profiles.profiles().forEach(profile -> paths.addAll(profile.ruleSets()));
        for (String path : paths) {
            Path resolved = resolveChild(root, path);
            if (!Files.isRegularFile(resolved)) {
                throw invalid("parser release rule set does not exist");
            }
            loaded.putIfAbsent(path, Files.readString(resolved, StandardCharsets.UTF_8));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
    }

    private ParserReleaseBundle.VerificationCase decodeCase(Path root, VerificationCaseDescriptor item) {
        if (item == null) {
            throw invalid("parser release verification case is invalid");
        }
        try {
            return new ParserReleaseBundle.VerificationCase(item.caseId(),
                    Files.readString(resolveChild(root, item.inputFile()), StandardCharsets.UTF_8),
                    Files.readString(resolveChild(root, item.expectedFile()), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                    "parser release verification files cannot be read", exception);
        }
    }

    private static Path resolveChild(Path root, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw invalid("parser release file path is required");
        }
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw invalid("parser release file must stay inside the release directory");
        }
        return resolved;
    }

    private static SemanticParserError invalid(String message) {
        return new SemanticParserError(SemanticParserError.INVALID_RULES, message);
    }

    private record VerificationDescriptor(List<VerificationCaseDescriptor> cases) {
    }

    private record VerificationCaseDescriptor(String caseId, String inputFile, String expectedFile) {
    }
}
