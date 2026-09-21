package com.dp.deviceops.parser.semantic.plan;

import com.dp.deviceops.parser.semantic.ParserCoordinate;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.SemanticParserSpecification;
import com.dp.deviceops.parser.semantic.input.CommandOutputBlockJsonInputAdapter;
import com.dp.deviceops.parser.semantic.input.LineLogInputAdapter;
import com.dp.deviceops.parser.semantic.input.ParserInputAdapter;
import com.dp.deviceops.parser.semantic.input.SectionTextInputAdapter;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalog;
import com.dp.deviceops.parser.semantic.internal.SemanticCatalogCodec;
import com.dp.deviceops.parser.semantic.internal.SemanticLimits;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ModelProfileCatalog;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledExtractor;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledModelProfile;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledRule;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledSelector;
import com.dp.deviceops.parser.semantic.plan.ParserPlan.CompiledStructureRule;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParserPlanCompiler {

    private static final Pattern NAMED_GROUP = Pattern.compile("\\(\\?<([A-Za-z][A-Za-z0-9]*)>");

    public static final String LEGACY_ENGINE_VERSION = "1.0.0";
    public static final String ENGINE_VERSION = "1.1.0";
    public static final String NESTED_ENGINE_VERSION = "1.2.0";
    public static final String STRUCTURED_ENGINE_VERSION = "1.3.0";
    public static final String ENHANCED_ENGINE_VERSION = "1.4.0";
    public static final Set<String> SUPPORTED_ENGINE_VERSIONS = Set.of(
            LEGACY_ENGINE_VERSION, ENGINE_VERSION, NESTED_ENGINE_VERSION, STRUCTURED_ENGINE_VERSION,
            ENHANCED_ENGINE_VERSION);

    private final SemanticCatalogCodec catalogCodec;
    private final Map<String, ParserInputAdapter> inputAdapters;
    private final ParserExtensionRegistry extensionRegistry;

    public ParserPlanCompiler() {
        this(SemanticLimits.defaults(), List.of(
                new CommandOutputBlockJsonInputAdapter(),
                new LineLogInputAdapter(),
                new SectionTextInputAdapter()), new ParserExtensionRegistry());
    }

    public ParserPlanCompiler(
            SemanticLimits limits,
            Collection<ParserInputAdapter> inputAdapters,
            ParserExtensionRegistry extensionRegistry) {
        this.catalogCodec = new SemanticCatalogCodec(new CanonicalJson(),
                Objects.requireNonNull(limits, "limits"));
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "extensionRegistry");
        Map<String, ParserInputAdapter> indexed = new LinkedHashMap<>();
        for (ParserInputAdapter adapter : Objects.requireNonNull(inputAdapters, "inputAdapters")) {
            if (indexed.putIfAbsent(adapter.inputFormat(), adapter) != null) {
                throw invalid("input adapter format must be unique");
            }
        }
        this.inputAdapters = Map.copyOf(indexed);
    }

    public ParserPlan compile(
            ParserCoordinate coordinate,
            String inputFormat,
            SemanticParserSpecification specification) {
        Objects.requireNonNull(coordinate, "coordinate");
        if (!SUPPORTED_ENGINE_VERSIONS.contains(coordinate.engineVersion())) {
            throw invalid("parser engine version is unsupported");
        }
        SemanticCatalog catalogs = catalogCodec.decode(Objects.requireNonNull(specification, "specification"));
        if (!coordinate.ruleVersion().equals(catalogs.ruleCatalog().catalogVersion())) {
            throw invalid("rule version does not match the catalog");
        }
        if (!coordinate.projectionVersion().equals(catalogs.projectionCatalog().catalogVersion())) {
            throw invalid("projection version does not match the catalog");
        }
        return compileDecoded(coordinate, inputFormat, catalogs);
    }

    public ParserPlan compile(ParserReleaseBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        var manifest = bundle.manifest();
        ExtensionCoordinate extension = manifest.extension();
        ParserCoordinate coordinate = new ParserCoordinate(
                manifest.logType(), manifest.releaseVersion(), manifest.engineVersion(),
                manifest.ruleVersion(), manifest.projectionVersion(),
                extension == null ? null : extension.extensionId(),
                extension == null ? null : extension.extensionVersion());
        if (LEGACY_ENGINE_VERSION.equals(manifest.engineVersion())) {
            return compile(coordinate, manifest.inputAdapter(), new SemanticParserSpecification(
                    bundle.rulesJson(), bundle.projectionsJson()));
        }
        if (!ENGINE_VERSION.equals(manifest.engineVersion())
                && !NESTED_ENGINE_VERSION.equals(manifest.engineVersion())
                && !STRUCTURED_ENGINE_VERSION.equals(manifest.engineVersion())
                && !ENHANCED_ENGINE_VERSION.equals(manifest.engineVersion())) {
            throw invalid("parser engine version is unsupported");
        }
        return compileModelAware(coordinate, manifest.inputAdapter(), bundle);
    }

    public ParserPlan compileLegacy(
            String logType,
            String releaseVersion,
            String inputFormat,
            SemanticParserSpecification specification) {
        SemanticCatalog catalogs = catalogCodec.decode(Objects.requireNonNull(specification, "specification"));
        ParserCoordinate coordinate = new ParserCoordinate(logType, releaseVersion, LEGACY_ENGINE_VERSION,
                catalogs.ruleCatalog().catalogVersion(), catalogs.projectionCatalog().catalogVersion(),
                null, null);
        return compileDecoded(coordinate, inputFormat, catalogs);
    }

    private ParserPlan compileDecoded(
            ParserCoordinate coordinate,
            String inputFormat,
            SemanticCatalog catalogs) {
        ParserInputAdapter adapter = inputAdapters.get(inputFormat);
        if (adapter == null) {
            throw invalid("parser input adapter is unavailable");
        }
        ParserExtension extension = coordinate.requiresExtension()
                ? extensionRegistry.find(new ExtensionCoordinate(
                        coordinate.extensionId(), coordinate.extensionVersion()))
                        .orElseThrow(() -> invalid("required parser extension is unavailable"))
                : null;
        validateCapabilities(coordinate.engineVersion(), List.of(catalogs));
        List<CompiledRule> rules = compileRules(catalogs.ruleCatalog().rules());
        List<CompiledStructureRule> structureRules = compileStructureRules(
                catalogs.ruleCatalog().structureRules());
        List<CompiledRule> discovery = rules.stream()
                .filter(ParserPlanCompiler::isModelDiscoveryRule).toList();
        return new ParserPlan(coordinate, inputFormat, adapter, catalogs, rules, discovery, structureRules,
                new CompiledModelProfile("generic", Set.of(), List.of(), List.of()),
                List.of(), extension);
    }

    private ParserPlan compileModelAware(
            ParserCoordinate coordinate,
            String inputFormat,
            ParserReleaseBundle bundle) {
        ModelProfileCatalog profiles = decodeProfiles(bundle.modelProfilesJson());
        Map<String, SemanticCatalog> catalogsByPath = new LinkedHashMap<>();
        bundle.ruleSetJsonByPath().forEach((path, json) -> catalogsByPath.put(path,
                catalogCodec.decode(new SemanticParserSpecification(json, bundle.projectionsJson()))));
        if (catalogsByPath.isEmpty()) {
            throw invalid("model-aware parser release has no rule sets");
        }
        for (SemanticCatalog catalog : catalogsByPath.values()) {
            if (!coordinate.ruleVersion().equals(catalog.ruleCatalog().catalogVersion())) {
                throw invalid("rule version does not match the catalog");
            }
            if (!coordinate.projectionVersion().equals(catalog.projectionCatalog().catalogVersion())) {
                throw invalid("projection version does not match the catalog");
            }
        }
        validateCapabilities(coordinate.engineVersion(), catalogsByPath.values());
        SemanticCatalog first = catalogsByPath.values().iterator().next();
        List<SemanticCatalog.Rule> aggregateRules = aggregateRules(catalogsByPath);
        List<SemanticCatalog.StructureRule> aggregateStructureRules = aggregateStructureRules(catalogsByPath);
        SemanticCatalog aggregate = new SemanticCatalog(
                new SemanticCatalog.RuleCatalog(aggregateStructureRules.isEmpty()
                        ? first.ruleCatalog().schemaVersion() : "1.1.0", coordinate.ruleVersion(),
                        aggregateStructureRules, aggregateRules),
                first.projectionCatalog());
        List<CompiledRule> baseRules = compileRules(rulesFor(profiles.baseRuleSets(), catalogsByPath));
        List<CompiledStructureRule> baseStructureRules = compileStructureRules(
                structureRulesFor(profiles.baseRuleSets(), catalogsByPath));
        List<CompiledRule> discoveryRules = baseRules.stream()
                .filter(ParserPlanCompiler::isModelDiscoveryRule).toList();
        if (discoveryRules.isEmpty()) {
            throw invalid("model-aware parser release requires a model discovery rule");
        }
        CompiledModelProfile generic = compileProfile(profiles.genericProfile(), catalogsByPath);
        List<CompiledModelProfile> modelProfiles = profiles.profiles().stream()
                .map(profile -> compileProfile(profile, catalogsByPath)).toList();
        return createPlan(coordinate, inputFormat, aggregate, baseRules, discoveryRules,
                baseStructureRules, generic, modelProfiles);
    }

    private ParserPlan createPlan(
            ParserCoordinate coordinate,
            String inputFormat,
            SemanticCatalog catalogs,
            List<CompiledRule> rules,
            List<CompiledRule> discoveryRules,
            List<CompiledStructureRule> structureRules,
            CompiledModelProfile generic,
            List<CompiledModelProfile> profiles) {
        ParserInputAdapter adapter = inputAdapters.get(inputFormat);
        if (adapter == null) {
            throw invalid("parser input adapter is unavailable");
        }
        ParserExtension extension = coordinate.requiresExtension()
                ? extensionRegistry.find(new ExtensionCoordinate(
                        coordinate.extensionId(), coordinate.extensionVersion()))
                        .orElseThrow(() -> invalid("required parser extension is unavailable"))
                : null;
        return new ParserPlan(coordinate, inputFormat, adapter, catalogs, rules, discoveryRules, structureRules,
                generic, profiles, extension);
    }

    private ModelProfileCatalog decodeProfiles(String json) {
        try {
            return new CanonicalJson().mapper().readValue(json, ModelProfileCatalog.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_RULES,
                    "model profile catalog is invalid", exception);
        }
    }

    private static List<SemanticCatalog.Rule> aggregateRules(Map<String, SemanticCatalog> catalogsByPath) {
        List<SemanticCatalog.Rule> aggregate = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        catalogsByPath.values().forEach(catalog -> catalog.ruleCatalog().rules().forEach(rule -> {
            if (!ids.add(rule.ruleId())) {
                throw invalid("ruleId must be unique across rule sets");
            }
            aggregate.add(rule);
        }));
        return List.copyOf(aggregate);
    }

    private static List<SemanticCatalog.StructureRule> aggregateStructureRules(
            Map<String, SemanticCatalog> catalogsByPath) {
        List<SemanticCatalog.StructureRule> aggregate = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        catalogsByPath.values().forEach(catalog -> catalog.ruleCatalog().structureRules().forEach(rule -> {
            if (!ids.add(rule.structureRuleId())) {
                throw invalid("structureRuleId must be unique across rule sets");
            }
            aggregate.add(rule);
        }));
        return List.copyOf(aggregate);
    }

    private static List<SemanticCatalog.Rule> rulesFor(
            List<String> paths,
            Map<String, SemanticCatalog> catalogsByPath) {
        List<SemanticCatalog.Rule> rules = new ArrayList<>();
        for (String path : paths) {
            SemanticCatalog catalog = catalogsByPath.get(path);
            if (catalog == null) {
                throw invalid("model profile references an unavailable rule set");
            }
            rules.addAll(catalog.ruleCatalog().rules());
        }
        return List.copyOf(rules);
    }

    private static List<SemanticCatalog.StructureRule> structureRulesFor(
            List<String> paths,
            Map<String, SemanticCatalog> catalogsByPath) {
        List<SemanticCatalog.StructureRule> rules = new ArrayList<>();
        for (String path : paths) {
            SemanticCatalog catalog = catalogsByPath.get(path);
            if (catalog == null) {
                throw invalid("model profile references an unavailable rule set");
            }
            rules.addAll(catalog.ruleCatalog().structureRules());
        }
        return List.copyOf(rules);
    }

    private static CompiledModelProfile compileProfile(
            ModelProfileCatalog.Profile profile,
            Map<String, SemanticCatalog> catalogsByPath) {
        Set<String> exact = profile.match().exact().stream()
                .map(ParserPlanCompiler::normalizeModel).collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<String> prefixes = profile.match().prefixes().stream()
                .map(ParserPlanCompiler::normalizeModel).toList();
        return new CompiledModelProfile(profile.profileId(), exact, prefixes,
                compileRules(rulesFor(profile.ruleSets(), catalogsByPath)),
                compileStructureRules(structureRulesFor(profile.ruleSets(), catalogsByPath)));
    }

    private static List<CompiledRule> compileRules(List<SemanticCatalog.Rule> rules) {
        return rules.stream().map(ParserPlanCompiler::compileRule).toList();
    }

    private static List<CompiledStructureRule> compileStructureRules(
            List<SemanticCatalog.StructureRule> rules) {
        return rules.stream().map(ParserPlanCompiler::compileStructureRule).toList();
    }

    private static CompiledStructureRule compileStructureRule(SemanticCatalog.StructureRule rule) {
        Map<String, Object> options = rule.options() == null ? Map.of() : rule.options();
        Pattern recordStartPattern = options.containsKey("recordStartPattern")
                ? Pattern.compile((String) options.get("recordStartPattern"), Pattern.CASE_INSENSITIVE)
                : null;
        List<String> columnNames = stringList(options.get("columnNames"));
        List<String> configSeparators = options.containsKey("configSeparators")
                ? stringList(options.get("configSeparators"))
                : "configStanza".equals(rule.type()) ? List.of("!") : List.of();
        return new CompiledStructureRule(rule,
                rule.selectors().stream().map(ParserPlanCompiler::compileSelector).toList(),
                recordStartPattern, columnNames, configSeparators);
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((List<?>) value).stream().map(String.class::cast).toList();
    }

    private static void validateCapabilities(
            String engineVersion,
            Collection<SemanticCatalog> catalogs) {
        boolean nested = catalogs.stream()
                .flatMap(catalog -> catalog.ruleCatalog().rules().stream())
                .flatMap(rule -> rule.extractors().stream())
                .anyMatch(SemanticCatalog.Extractor::emitNestedUnits);
        if (nested && !Set.of(NESTED_ENGINE_VERSION, STRUCTURED_ENGINE_VERSION,
                ENHANCED_ENGINE_VERSION).contains(engineVersion)) {
            throw invalid("nested units require parser engine version 1.2.0");
        }
        boolean structured = catalogs.stream()
                .anyMatch(catalog -> !catalog.ruleCatalog().structureRules().isEmpty());
        if (structured && !Set.of(STRUCTURED_ENGINE_VERSION, ENHANCED_ENGINE_VERSION).contains(engineVersion)) {
            throw invalid("structure rules require parser engine version 1.3.0");
        }
    }

    private static boolean isModelDiscoveryRule(CompiledRule rule) {
        return "device.identity.model".equals(rule.source().target().semanticKey());
    }

    private static String normalizeModel(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static CompiledRule compileRule(SemanticCatalog.Rule rule) {
        List<CompiledSelector> selectors = rule.roleSelectors().stream()
                .map(ParserPlanCompiler::compileSelector).toList();
        List<CompiledExtractor> extractors = rule.extractors().stream()
                .map(ParserPlanCompiler::compileExtractor).toList();
        return new CompiledRule(rule, selectors, extractors);
    }

    private static CompiledSelector compileSelector(SemanticCatalog.Selector selector) {
        Pattern pattern = switch (selector.type()) {
            case "COMMAND_REGEX" -> Pattern.compile(selector.value(), Pattern.CASE_INSENSITIVE);
            case "CONTENT_REGEX" -> Pattern.compile(selector.value(),
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
            default -> null;
        };
        return new CompiledSelector(selector, pattern);
    }

    private static CompiledExtractor compileExtractor(SemanticCatalog.Extractor extractor) {
        if (extractor.emitNestedUnits()
                && (!"DELIMITED_SECTION".equals(extractor.type())
                || extractor.headerGroup() == null
                || extractor.headerGroup().isBlank())) {
            throw invalid("nested units require a delimited section command group");
        }
        if (extractor.emitNestedUnits()
                && !hasNamedGroup(extractor.startPattern(), extractor.headerGroup())) {
            throw invalid("nested units require an existing named command group");
        }
        Pattern pattern = switch (extractor.type()) {
            case "LINE_REGEX", "LINE_REGEX_OBJECT", "STATUS_TEXT" -> Pattern.compile(
                    extractor.pattern(), Pattern.CASE_INSENSITIVE);
            default -> null;
        };
        Pattern startPattern = "DELIMITED_SECTION".equals(extractor.type())
                ? Pattern.compile(extractor.startPattern(), Pattern.CASE_INSENSITIVE)
                : null;
        return new CompiledExtractor(extractor, pattern, startPattern);
    }

    private static boolean hasNamedGroup(String expression, String expected) {
        Matcher matcher = NAMED_GROUP.matcher(expression);
        while (matcher.find()) {
            if (expected.equals(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static SemanticParserError invalid(String message) {
        return new SemanticParserError(SemanticParserError.INVALID_RULES, message);
    }
}
