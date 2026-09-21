package com.dp.deviceops.parser.semantic.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public record SemanticCatalog(RuleCatalog ruleCatalog, ProjectionCatalog projectionCatalog) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleCatalog(
            String schemaVersion,
            String catalogVersion,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<StructureRule> structureRules,
            List<Rule> rules) {

        public RuleCatalog {
            structureRules = structureRules == null ? List.of() : List.copyOf(structureRules);
        }

        public RuleCatalog(String schemaVersion, String catalogVersion, List<Rule> rules) {
            this(schemaVersion, catalogVersion, List.of(), rules);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StructureRule(
            String structureRuleId,
            List<Selector> selectors,
            String mode,
            String type,
            Map<String, Object> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Rule(
            String ruleId,
            String blockRole,
            List<Selector> roleSelectors,
            List<Extractor> extractors,
            Target target,
            List<String> aliases,
            Double confidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Selector(String type, String value, List<String> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Extractor(
            String type,
            String pattern,
            Integer group,
            String key,
            Object value,
            List<String> transforms,
            @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, ObjectField> fields,
            @JsonInclude(JsonInclude.Include.NON_NULL) String startPattern,
            @JsonInclude(JsonInclude.Include.NON_NULL) String headerGroup,
            @JsonInclude(JsonInclude.Include.NON_DEFAULT)
            boolean emitNestedUnits) {

        public Extractor(
                String type,
                String pattern,
                Integer group,
                String key,
                Object value,
                List<String> transforms,
                Map<String, ObjectField> fields,
                String startPattern,
                String headerGroup) {
            this(type, pattern, group, key, value, transforms, fields, startPattern, headerGroup, false);
        }

        public Extractor(
                String type,
                String pattern,
                Integer group,
                String key,
                Object value,
                List<String> transforms) {
            this(type, pattern, group, key, value, transforms, null, null, null, false);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ObjectField(String group, List<String> transforms, Boolean required) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Target(
            String semanticKey,
            String cardinality,
            String dataType,
            String conflictPolicy,
            String unit) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectionCatalog(
            String schemaVersion,
            String catalogVersion,
            List<ProjectionProfile> profiles) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProjectionProfile(
            String projectionId,
            String description,
            String missingValuePolicy,
            Map<String, String> fields) {
    }
}
