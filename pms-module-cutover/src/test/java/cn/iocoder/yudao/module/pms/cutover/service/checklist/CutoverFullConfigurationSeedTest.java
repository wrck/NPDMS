package cn.iocoder.yudao.module.pms.cutover.service.checklist;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.domain.configuration.*;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual forward migration payload, including its matching examples. */
class CutoverFullConfigurationSeedTest {
    @Test
    void standardSeedPassesPublishRulesAndHasAllNinetySevenNamedChecks() throws Exception {
        JsonNode seed = seed("cut_seed");
        List<CutoverConfigurationRules.ItemDefinition> items = items(seed);
        List<CutoverConfigurationRules.BindingRule> rules = rules(seed);
        var dimensions = new ArrayList<CutoverConfigurationRules.DimensionDefinition>();
        for (var n : seed.path("dimensions")) dimensions.add(new CutoverConfigurationRules.DimensionDefinition(
                value(n, "code"), value(n, "name"), value(n, "dataType"), value(n, "valueSource"),
                value(n, "owner"), value(n, "contextPath"), n.path("enabled").asBoolean()));
        var sections = new ArrayList<CutoverConfigurationRules.PlanTemplateSection>();
        for (var n : seed.path("planTemplateSections")) sections.add(new CutoverConfigurationRules.PlanTemplateSection(
                value(n, "stableSectionKey"), value(n, "title"), n.path("sortOrder").asInt(),
                values(n.path("cutoverTypeCodes")), values(n.path("levelCodes")), n.path("required").asBoolean()));
        var context = new CutoverMatrixValidationContext(dictionary(seed, "pms_cutover_task_type"),
                dictionary(seed, "pms_cutover_task_network_mode"), dictionary(seed, "pms_device_type"), dictionary(seed, "pms_risk_level"));
        var errors = new ArrayList<>(CutoverConfigurationRules.validate(dimensions, items, rules, sections));
        errors.addAll(CutoverRiskMatrixRules.validate(items, rules, context));
        errors.addAll(CutoverSurveyMatrixRules.validate(items, rules, context));
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(133, items.size());
        for (var entry : CutoverRiskMatrixRules.DUAL_COUNTS.entrySet()) {
            assertEquals(entry.getValue().longValue(), items.stream().filter(i -> entry.getKey().equals(i.subtableCode())).count());
        }
        assertEquals(Set.of("OPERATION", "ROLLBACK"), sections.stream().filter(s -> s.levelCodes().contains("D"))
                .map(CutoverConfigurationRules.PlanTemplateSection::stableSectionKey).collect(java.util.stream.Collectors.toSet()));
        assertEquals(10, context.cutoverTypeCodes().size());
    }

    @Test
    void examplesExerciseExactPartialPriorityGapAndDisabledRules() throws Exception {
        var config = frozen(seed("cut_seed"));
        var matcher = new CutoverRuntimeChecklistMatcher();
        var exact = matcher.match(config, input("VERSION_UPGRADE", "VSM", "FW", "A"));
        assertTrue(exact.conflicts().isEmpty());
        assertTrue(exact.readyItems().stream().anyMatch(v -> v.item().stableItemKey().equals("EXAMPLE_EXACT")));
        assertTrue(exact.readyItems().stream().anyMatch(v -> v.item().stableItemKey().equals("EXAMPLE_PARTIAL")));
        assertFalse(exact.readyItems().stream().filter(v -> v.item().stableItemKey().equals("EXAMPLE_PRIORITY"))
                .findFirst().orElseThrow().required(), "narrow high-priority optional rule must override broad required rule");
        assertFalse(exact.readyItems().stream().anyMatch(v -> v.item().stableItemKey().equals("EXAMPLE_DISABLED")));
        var partial = matcher.match(config, input("DEVICE_ONBOARD", "VSM", "SW", "B"));
        assertFalse(partial.readyItems().stream().anyMatch(v -> v.item().stableItemKey().equals("EXAMPLE_EXACT")));
        assertTrue(partial.readyItems().stream().anyMatch(v -> v.item().stableItemKey().equals("EXAMPLE_PARTIAL")));
        assertTrue(matcher.match(config, input("VERSION_UPGRADE", "VSM", "FW", "D")).gap());
    }

    private static CutoverRuntimeChecklistMatcher.MatchInput input(String type, String mode, String device, String level) {
        return new CutoverRuntimeChecklistMatcher.MatchInput(Map.of("CUTOVER_TYPE", List.of(type), "NETWORK_MODE", List.of(mode),
                "DEVICE_TYPE", List.of(device), "CUTOVER_LEVEL", List.of(level)));
    }

    private static CutoverFrozenConfiguration frozen(JsonNode seed) {
        var items = new ArrayList<CutoverFrozenConfiguration.ItemDefinition>();
        var ids = new HashMap<String, Long>();
        for (var n : combined(seed, "items", "exampleItems")) {
            if (!n.path("enabled").asBoolean()) continue;
            long id = items.size() + 1L;
            ids.put(value(n, "stableItemKey"), id);
            items.add(new CutoverFrozenConfiguration.ItemDefinition(id, value(n, "stableItemKey"), 1, value(n, "itemType"),
                    value(n, "itemName"), value(n, "itemDescription"), value(n, "interfaceFormat"), n.path("interfaceSchema").toString(),
                    value(n, "workMode"), n.path("required").asBoolean(), n.path("sortOrder").asInt()));
        }
        var rules = new ArrayList<CutoverFrozenConfiguration.BindingRule>();
        for (var n : combined(seed, "bindingRules", "exampleRules")) {
            if (!n.path("enabled").asBoolean()) continue;
            rules.add(new CutoverFrozenConfiguration.BindingRule(rules.size() + 1L, value(n, "stableRuleKey"),
                    ids.get(value(n, "stableItemKey")), 1, n.path("dimensionConditions").toString(), n.path("priority").asInt(), n.path("requiredResult").asBoolean()));
        }
        return new CutoverFrozenConfiguration(1L, "SEED", 1, "PUBLISHED", seed.path("dictionarySnapshot").toString(),
                seed.path("dimensions").toString(), items, rules);
    }

    private static List<CutoverConfigurationRules.ItemDefinition> items(JsonNode seed) {
        var items = new ArrayList<CutoverConfigurationRules.ItemDefinition>();
        for (var n : seed.path("items")) items.add(new CutoverConfigurationRules.ItemDefinition(value(n, "stableItemKey"), value(n, "itemType"),
                value(n, "businessCategoryCode"), value(n, "itemName"), value(n, "interfaceFormat"),
                JsonUtils.parseObject(n.path("interfaceSchema").toString(), Map.class), value(n, "feedbackFormat"), n.path("required").asBoolean(),
                value(n, "workMode"), null, n.path("subtableCode").isNull() ? null : value(n, "subtableCode"), n.path("enabled").asBoolean()));
        return items;
    }

    private static List<CutoverConfigurationRules.BindingRule> rules(JsonNode seed) {
        var rules = new ArrayList<CutoverConfigurationRules.BindingRule>();
        for (var n : seed.path("bindingRules")) rules.add(new CutoverConfigurationRules.BindingRule(value(n, "stableRuleKey"), value(n, "stableItemKey"),
                n.path("dimensionConditions").toString(), n.path("priority").asInt(), n.path("requiredResult").asBoolean(), n.path("enabled").asBoolean()));
        return rules;
    }

    private static Set<String> dictionary(JsonNode seed, String name) {
        var values = new LinkedHashSet<String>();
        seed.path("dictionarySnapshot").path(name).forEach(n -> values.add(value(n, "value")));
        return values;
    }
    private static List<String> values(JsonNode node) { var result = new ArrayList<String>(); node.forEach(n -> result.add(n.asText())); return result; }
    private static List<JsonNode> combined(JsonNode seed, String first, String second) {
        var result = new ArrayList<JsonNode>(); seed.path(first).forEach(result::add); seed.path(second).forEach(result::add); return result;
    }
    private static String value(JsonNode node, String key) { return node.path(key).asText(); }
    private static JsonNode seed(String variable) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("sql/migrations"))) root = root.getParent();
        assertNotNull(root);
        String sql = Files.readString(root.resolve("sql/migrations/V452__cutover_full_configuration_seed.sql"));
        String marker = "SET @" + variable + " = '";
        int start = sql.indexOf(marker);
        assertTrue(start >= 0, "migration variable " + variable);
        start += marker.length();
        int end = sql.indexOf("';", start);
        return JsonUtils.parseTree(sql.substring(start, end).replace("''", "'").replace("\\\\", "\\"));
    }
}
