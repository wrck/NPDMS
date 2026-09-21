package com.dp.deviceops.parser.semantic.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.SemanticParserSpecification;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.dp.deviceops.parser.semantic.internal.SemanticCatalog.*;

public final class SemanticCatalogCodec {

    private static final Set<String> SELECTORS = Set.of(
            "COMMAND_ALIAS", "COMMAND_REGEX", "CONTENT_REGEX", "TABLE_HEADERS");
    private static final Set<String> EXTRACTORS = Set.of(
            "LINE_REGEX", "LINE_REGEX_OBJECT", "KEY_VALUE", "TABLE", "CONFIG_STANZA",
            "RECORD_LIST", "LIST", "STRUCTURE_SECTION", "DELIMITED_SECTION", "STATUS_TEXT");
    private static final Set<String> TRANSFORMS = Set.of(
            "trim", "lowercase", "integer", "decimal", "boolean-enable-disable",
            "capacity-to-bytes", "duration-to-seconds", "datetime");
    private static final Set<String> DATA_TYPES = Set.of(
            "string", "integer", "decimal", "boolean", "datetime", "object");
    private static final Set<String> STRUCTURE_MODES = Set.of("AUTO", "FORCE", "TEXT");
    private static final Set<String> STRUCTURE_TYPES = Set.of(
            "keyValue", "keyValueTree", "table", "recordList", "configStanza", "list", "text");
    private static final Map<String, Set<String>> STRUCTURE_OPTIONS = Map.of(
            "recordList", Set.of("recordStartPattern"),
            "table", Set.of("columnNames"),
            "configStanza", Set.of("configSeparators"));
    private static final Set<String> FORBIDDEN_SEGMENTS = Set.of("__proto__", "prototype", "constructor");

    private final ObjectMapper mapper;
    private final SemanticLimits limits;

    public SemanticCatalogCodec(CanonicalJson canonicalJson, SemanticLimits limits) {
        this.mapper = Objects.requireNonNull(canonicalJson, "canonicalJson").mapper();
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public SemanticCatalog decode(SemanticParserSpecification specification) {
        RuleCatalog rules = read(specification.ruleCatalogJson(), RuleCatalog.class,
                SemanticParserError.INVALID_RULES, "rule catalog is invalid");
        ProjectionCatalog projections = read(specification.projectionCatalogJson(), ProjectionCatalog.class,
                SemanticParserError.INVALID_PROJECTIONS, "projection catalog is invalid");
        validateRules(rules);
        validateProjections(projections);
        return new SemanticCatalog(rules, projections);
    }

    private <T> T read(String json, Class<T> type, String code, String message) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new SemanticParserError(code, message, exception);
        }
    }

    private void validateRules(RuleCatalog catalog) {
        if (catalog.schemaVersion() == null
                || !Set.of("1.0.0", "1.1.0").contains(catalog.schemaVersion())) {
            invalidRules("rule schemaVersion must equal 1.0.0 or 1.1.0");
        }
        requireText(catalog.catalogVersion(), SemanticParserError.INVALID_RULES, "rule catalogVersion");
        List<StructureRule> structureRules = requireList(catalog.structureRules(),
                SemanticParserError.INVALID_RULES, "structureRules");
        if ("1.0.0".equals(catalog.schemaVersion()) && !structureRules.isEmpty()) {
            invalidRules("structureRules require rule schemaVersion 1.1.0");
        }
        Set<String> structureIds = new HashSet<>();
        for (StructureRule structureRule : structureRules) {
            validateStructureRule(structureRule, structureIds);
        }
        List<Rule> rules = requireList(catalog.rules(), SemanticParserError.INVALID_RULES, "rules");
        if (rules.size() > limits.maxRules()) {
            resourceLimit("rule count exceeds limit");
        }
        Set<String> ids = new HashSet<>();
        for (Rule rule : rules) {
            if (rule == null) {
                invalidRules("rules must not contain null");
            }
            String ruleId = requireText(rule.ruleId(), SemanticParserError.INVALID_RULES, "ruleId");
            if (!ids.add(ruleId)) {
                invalidRules("ruleId must be unique");
            }
            requireText(rule.blockRole(), SemanticParserError.INVALID_RULES, "blockRole");
            List<Selector> selectors = requireList(rule.roleSelectors(), SemanticParserError.INVALID_RULES,
                    "roleSelectors");
            if (selectors.isEmpty()) {
                invalidRules("roleSelectors must not be empty");
            }
            selectors.forEach(this::validateSelector);
            requireList(rule.extractors(), SemanticParserError.INVALID_RULES, "extractors")
                    .forEach(this::validateExtractor);
            Target target = rule.target();
            if (target == null) {
                invalidRules("target must be an object");
            }
            validatePath(target.semanticKey(), SemanticParserError.INVALID_RULES, "semanticKey");
            if (!Set.of("ONE", "MANY").contains(target.cardinality())) {
                invalidRules("unsupported cardinality");
            }
            if (!DATA_TYPES.contains(target.dataType())) {
                invalidRules("unsupported dataType");
            }
            if (!Set.of("HIGHEST_CONFIDENCE", "APPEND_DISTINCT").contains(target.conflictPolicy())) {
                invalidRules("unsupported conflictPolicy");
            }
        }
    }

    private void validateStructureRule(StructureRule rule, Set<String> ids) {
        if (rule == null) {
            invalidRules("structureRules must not contain null");
        }
        String id = requireText(rule.structureRuleId(), SemanticParserError.INVALID_RULES,
                "structureRuleId");
        if (!ids.add(id)) {
            invalidRules("structureRuleId must be unique");
        }
        List<Selector> selectors = requireList(rule.selectors(), SemanticParserError.INVALID_RULES,
                "structure selectors");
        if (selectors.isEmpty()) {
            invalidRules("structure selectors must not be empty");
        }
        selectors.forEach(this::validateSelector);
        if (rule.mode() == null || !STRUCTURE_MODES.contains(rule.mode())) {
            invalidRules("unsupported structure mode");
        }
        Map<String, Object> options = rule.options() == null ? Map.of() : rule.options();
        if (!"FORCE".equals(rule.mode())) {
            if (rule.type() != null || !options.isEmpty()) {
                invalidRules("AUTO and TEXT structure rules must not declare type or options");
            }
            return;
        }
        if (rule.type() == null || !STRUCTURE_TYPES.contains(rule.type())) {
            invalidRules("unsupported structure type");
        }
        Set<String> allowed = STRUCTURE_OPTIONS.getOrDefault(rule.type(), Set.of());
        if (!allowed.containsAll(options.keySet())) {
            invalidRules("unsupported structure option");
        }
        validateStructureOptions(rule.type(), options);
    }

    private void validateStructureOptions(String type, Map<String, Object> options) {
        if ("recordList".equals(type) && options.containsKey("recordStartPattern")) {
            Object value = options.get("recordStartPattern");
            if (!(value instanceof String) || ((String) value).isBlank()) {
                invalidRules("recordStartPattern must be text");
            }
            String regex = (String) value;
            validateRegex(regex);
            if (!namedGroups(regex).contains("id")) {
                invalidRules("recordStartPattern must declare named group id");
            }
        }
        if ("table".equals(type) && options.containsKey("columnNames")) {
            requireNonBlankStringList(options.get("columnNames"), "columnNames");
        }
        if ("configStanza".equals(type) && options.containsKey("configSeparators")) {
            requireNonBlankStringList(options.get("configSeparators"), "configSeparators");
        }
    }

    private static void requireNonBlankStringList(Object value, String name) {
        if (!(value instanceof List<?> values) || values.isEmpty()
                || values.stream().anyMatch(item -> !(item instanceof String text) || text.isBlank())) {
            invalidRules(name + " must be a non-empty string array");
        }
    }

    private void validateSelector(Selector selector) {
        if (selector == null || !SELECTORS.contains(selector.type())) {
            invalidRules("unsupported selector type");
        }
        if ("TABLE_HEADERS".equals(selector.type())) {
            List<String> values = requireList(selector.values(), SemanticParserError.INVALID_RULES,
                    "selector values");
            if (values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
                invalidRules("table headers must not be empty");
            }
            return;
        }
        String value = requireText(selector.value(), SemanticParserError.INVALID_RULES, "selector value");
        if (Set.of("COMMAND_REGEX", "CONTENT_REGEX").contains(selector.type())) {
            validateRegex(value);
        }
    }

    private void validateExtractor(Extractor extractor) {
        if (extractor == null || !EXTRACTORS.contains(extractor.type())) {
            invalidRules("unsupported extractor type");
        }
        if (Set.of("LINE_REGEX", "STATUS_TEXT").contains(extractor.type())) {
            validateRegex(requireText(extractor.pattern(), SemanticParserError.INVALID_RULES,
                    "extractor pattern"));
        }
        if ("LINE_REGEX_OBJECT".equals(extractor.type())) {
            String regex = requireText(extractor.pattern(), SemanticParserError.INVALID_RULES,
                    "extractor pattern");
            validateRegex(regex);
            Map<String, ObjectField> fields = extractor.fields();
            if (fields == null || fields.isEmpty()) {
                invalidRules("object extractor fields must not be empty");
            }
            Set<String> groups = namedGroups(regex);
            fields.forEach((fieldName, field) -> {
                requireText(fieldName, SemanticParserError.INVALID_RULES, "object field name");
                if (field == null || !groups.contains(field.group())) {
                    invalidRules("object field references an unavailable named group");
                }
                validateTransforms(field.transforms());
            });
        }
        if ("DELIMITED_SECTION".equals(extractor.type())) {
            String regex = requireText(extractor.startPattern(), SemanticParserError.INVALID_RULES,
                    "section startPattern");
            validateRegex(regex);
            String headerGroup = requireText(extractor.headerGroup(), SemanticParserError.INVALID_RULES,
                    "section headerGroup");
            if (!namedGroups(regex).contains(headerGroup)) {
                invalidRules("section headerGroup is unavailable");
            }
        }
        if ("KEY_VALUE".equals(extractor.type())) {
            requireText(extractor.key(), SemanticParserError.INVALID_RULES, "extractor key");
        }
        validateTransforms(extractor.transforms());
    }

    private void validateTransforms(List<String> transforms) {
        for (String transform : transforms == null ? List.<String>of() : transforms) {
            if (!TRANSFORMS.contains(transform)) {
                invalidRules("unsupported transform");
            }
        }
    }

    private static Set<String> namedGroups(String regex) {
        Set<String> groups = new HashSet<>();
        boolean escaped = false;
        boolean characterClass = false;
        for (int index = 0; index < regex.length(); index++) {
            char current = regex.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '[') {
                characterClass = true;
                continue;
            }
            if (current == ']' && characterClass) {
                characterClass = false;
                continue;
            }
            if (!characterClass && current == '(' && index + 3 < regex.length()
                    && regex.startsWith("(?<", index)
                    && regex.charAt(index + 3) != '=' && regex.charAt(index + 3) != '!') {
                int end = regex.indexOf('>', index + 3);
                if (end > index + 3) {
                    groups.add(regex.substring(index + 3, end));
                }
            }
        }
        return groups;
    }

    private void validateProjections(ProjectionCatalog catalog) {
        requireVersion(catalog.schemaVersion(), SemanticParserError.INVALID_PROJECTIONS,
                "projection schemaVersion");
        requireText(catalog.catalogVersion(), SemanticParserError.INVALID_PROJECTIONS,
                "projection catalogVersion");
        Set<String> ids = new HashSet<>();
        int fields = 0;
        for (ProjectionProfile profile : requireList(catalog.profiles(),
                SemanticParserError.INVALID_PROJECTIONS, "profiles")) {
            if (profile == null) {
                invalidProjections("profiles must not contain null");
            }
            String id = requireText(profile.projectionId(), SemanticParserError.INVALID_PROJECTIONS,
                    "projectionId");
            if (!ids.add(id)) {
                invalidProjections("projectionId must be unique");
            }
            if (!Set.of("NULL", "OMIT").contains(profile.missingValuePolicy())) {
                invalidProjections("unsupported missingValuePolicy");
            }
            Map<String, String> mapping = profile.fields();
            if (mapping == null) {
                invalidProjections("projection fields must be an object");
            }
            fields += mapping.size();
            for (Map.Entry<String, String> field : mapping.entrySet()) {
                validatePath("projection." + field.getKey(), SemanticParserError.INVALID_PROJECTIONS,
                        "projection field");
                validatePath(field.getValue(), SemanticParserError.INVALID_PROJECTIONS, "semantic path");
            }
        }
        if (fields > limits.maxProjectionFields()) {
            resourceLimit("projection field count exceeds limit");
        }
    }

    private void validateRegex(String regex) {
        if (regex.length() > limits.maxRegexLength()) {
            resourceLimit("regular expression exceeds limit");
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                    "regular expression is invalid", exception);
        }
    }

    private static void validatePath(String path, String code, String name) {
        String value = requireText(path, code, name);
        for (String segment : value.split("\\.", -1)) {
            if (segment.isBlank() || FORBIDDEN_SEGMENTS.contains(segment)) {
                throw new SemanticParserError(code, name + " is unsafe");
            }
        }
    }

    private static void requireVersion(String value, String code, String name) {
        if (!"1.0.0".equals(value)) {
            throw new SemanticParserError(code, name + " must equal 1.0.0");
        }
    }

    private static String requireText(String value, String code, String name) {
        if (value == null || value.isBlank()) {
            throw new SemanticParserError(code, name + " must not be blank");
        }
        return value;
    }

    private static <T> List<T> requireList(List<T> value, String code, String name) {
        if (value == null) {
            throw new SemanticParserError(code, name + " must be an array");
        }
        return value;
    }

    private static void invalidRules(String message) {
        throw new SemanticParserError(SemanticParserError.INVALID_RULES, message);
    }

    private static void invalidProjections(String message) {
        throw new SemanticParserError(SemanticParserError.INVALID_PROJECTIONS, message);
    }

    private static void resourceLimit(String message) {
        throw new SemanticParserError(SemanticParserError.RESOURCE_LIMIT, message);
    }
}
