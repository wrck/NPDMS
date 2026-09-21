package com.dp.deviceops.parser.semantic.plan;

import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.input.ParserInputAdapter;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record ParserPlan(
        ParserCoordinate coordinate,
        String inputFormat,
        ParserInputAdapter inputAdapter,
        SemanticCatalog catalogs,
        List<CompiledRule> rules,
        List<CompiledRule> discoveryRules,
        List<CompiledStructureRule> structureRules,
        CompiledModelProfile genericProfile,
        List<CompiledModelProfile> modelProfiles,
        ParserExtension extension) {

    public ParserPlan {
        Objects.requireNonNull(coordinate, "coordinate");
        inputFormat = Objects.requireNonNull(inputFormat, "inputFormat");
        Objects.requireNonNull(inputAdapter, "inputAdapter");
        Objects.requireNonNull(catalogs, "catalogs");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
        discoveryRules = List.copyOf(Objects.requireNonNull(discoveryRules, "discoveryRules"));
        structureRules = List.copyOf(Objects.requireNonNull(structureRules, "structureRules"));
        Objects.requireNonNull(genericProfile, "genericProfile");
        modelProfiles = List.copyOf(Objects.requireNonNull(modelProfiles, "modelProfiles"));
    }

    public ParserPlan(ParserCoordinate coordinate, String inputFormat, ParserInputAdapter inputAdapter,
            SemanticCatalog catalogs, List<CompiledRule> rules, ParserExtension extension) {
        this(coordinate, inputFormat, inputAdapter, catalogs, rules, List.of(), List.of(),
                new CompiledModelProfile("generic", Set.of(), List.of(), List.of()),
                List.of(), extension);
    }

    public record CompiledSelector(SemanticCatalog.Selector source, Pattern pattern) {
        public CompiledSelector {
            Objects.requireNonNull(source, "source");
        }
    }

    public record CompiledExtractor(
            SemanticCatalog.Extractor source,
            Pattern pattern,
            Pattern startPattern) {
        public CompiledExtractor {
            Objects.requireNonNull(source, "source");
        }

        public CompiledExtractor(SemanticCatalog.Extractor source, Pattern pattern) {
            this(source, pattern, null);
        }
    }

    public record CompiledRule(
            SemanticCatalog.Rule source,
            List<CompiledSelector> selectors,
            List<CompiledExtractor> extractors) {
        public CompiledRule {
            Objects.requireNonNull(source, "source");
            selectors = List.copyOf(Objects.requireNonNull(selectors, "selectors"));
            extractors = List.copyOf(Objects.requireNonNull(extractors, "extractors"));
        }
    }

    public record CompiledStructureRule(
            SemanticCatalog.StructureRule source,
            List<CompiledSelector> selectors,
            Pattern recordStartPattern,
            List<String> columnNames,
            List<String> configSeparators) {
        public CompiledStructureRule {
            Objects.requireNonNull(source, "source");
            selectors = List.copyOf(Objects.requireNonNull(selectors, "selectors"));
            columnNames = List.copyOf(Objects.requireNonNull(columnNames, "columnNames"));
            configSeparators = List.copyOf(Objects.requireNonNull(configSeparators, "configSeparators"));
        }
    }

    public record CompiledModelProfile(
            String profileId,
            Set<String> exactModels,
            List<String> modelPrefixes,
            List<CompiledRule> rules,
            List<CompiledStructureRule> structureRules) {
        public CompiledModelProfile {
            profileId = Objects.requireNonNull(profileId, "profileId");
            exactModels = Set.copyOf(Objects.requireNonNull(exactModels, "exactModels"));
            modelPrefixes = List.copyOf(Objects.requireNonNull(modelPrefixes, "modelPrefixes"));
            rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
            structureRules = List.copyOf(Objects.requireNonNull(structureRules, "structureRules"));
        }

        public CompiledModelProfile(
                String profileId,
                Set<String> exactModels,
                List<String> modelPrefixes,
                List<CompiledRule> rules) {
            this(profileId, exactModels, modelPrefixes, rules, List.of());
        }
    }
}
