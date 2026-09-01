package cn.iocoder.yudao.module.pms.cutover.domain.configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CutoverMatrixFixtures {

    private CutoverMatrixFixtures() {
    }

    static List<CutoverConfigurationRules.ItemDefinition> riskItems(Map<String, Integer> dualCounts) {
        List<CutoverConfigurationRules.ItemDefinition> items = new ArrayList<>();
        for (String category : CutoverRiskMatrixRules.REQUIRED_RISK_CATEGORIES) {
            items.add(item("RISK_" + category, "RISK", category, null, Map.of()));
        }
        dualCounts.forEach((mode, count) -> {
            for (int index = 1; index <= count; index++) {
                items.add(item("DUAL_" + mode + "_" + "%03d".formatted(index),
                        "DUAL_MACHINE_CHECK", mode, mode, Map.of()));
            }
        });
        return items;
    }

    static List<CutoverConfigurationRules.ItemDefinition> completeRiskItems() {
        return riskItems(CutoverRiskMatrixRules.DUAL_COUNTS);
    }

    static List<CutoverConfigurationRules.BindingRule> completeRiskRules() {
        List<CutoverConfigurationRules.BindingRule> rules = new ArrayList<>();
        for (String category : CutoverRiskMatrixRules.ALL_SITUATION_REQUIRED) {
            rules.add(new CutoverConfigurationRules.BindingRule("RULE_" + category,
                    "RISK_" + category,
                    conditions(Map.of(
                            "CUTOVER_TYPE", List.copyOf(context().cutoverTypeCodes()),
                            "DEVICE_TYPE", List.copyOf(context().deviceTypeCodes()),
                            "CUTOVER_LEVEL", List.of("A", "B", "C"))),
                    10, true, true));
        }
        rules.add(new CutoverConfigurationRules.BindingRule("RULE_TARGET_VERSION",
                "RISK_TARGET_VERSION_BULLETIN",
                conditions(Map.of("CUTOVER_TYPE", List.of("VERSION_UPGRADE"))),
                10, true, true));
        for (String category : CutoverRiskMatrixRules.REQUIRED_RISK_CATEGORIES) {
            boolean alreadyBound = rules.stream()
                    .anyMatch(rule -> rule.stableItemKey().equals("RISK_" + category));
            if (!alreadyBound) {
                rules.add(new CutoverConfigurationRules.BindingRule("RULE_" + category + "_BASE",
                        "RISK_" + category,
                        conditions(Map.of("CUTOVER_LEVEL", List.of("A", "B", "C"))),
                        10, false, true));
            }
        }
        CutoverRiskMatrixRules.DUAL_COUNTS.forEach((mode, count) -> {
            for (int index = 1; index <= count; index++) {
                String stableItemKey = "DUAL_" + mode + "_" + "%03d".formatted(index);
                rules.add(rule(stableItemKey, mode, true));
            }
        });
        return rules;
    }

    static List<CutoverConfigurationRules.BindingRule> rulesMissingDevice(String category, String deviceType) {
        List<CutoverConfigurationRules.BindingRule> rules = new ArrayList<>(completeRiskRules());
        rules.removeIf(rule -> rule.stableItemKey().equals("RISK_" + category));
        List<String> devices = context().deviceTypeCodes().stream()
                .filter(code -> !code.equals(deviceType))
                .toList();
        rules.add(new CutoverConfigurationRules.BindingRule("RULE_" + category + "_INCOMPLETE",
                "RISK_" + category,
                conditions(Map.of(
                        "CUTOVER_TYPE", List.copyOf(context().cutoverTypeCodes()),
                        "DEVICE_TYPE", devices,
                        "CUTOVER_LEVEL", List.of("A", "B", "C"))),
                10, true, true));
        return rules;
    }

    static List<CutoverConfigurationRules.ItemDefinition> completeSurveyItems() {
        List<CutoverConfigurationRules.ItemDefinition> items = new ArrayList<>();
        List<String> categories = new ArrayList<>(CutoverSurveyMatrixRules.CORE_SURVEY_CATEGORIES);
        categories.remove("CUTOVER_BACKGROUND");
        categories.sort(String::compareTo);
        categories.addFirst("CUTOVER_BACKGROUND");
        for (String category : categories) {
            Map<String, Object> schema = "CUTOVER_BACKGROUND".equals(category)
                    ? backgroundSchema() : new LinkedHashMap<>();
            items.add(item("SURVEY_" + category, "BUSINESS_SURVEY", category, null, schema));
        }
        return items;
    }

    static List<CutoverConfigurationRules.ItemDefinition> completeSurveyItemsWithout(String category) {
        List<CutoverConfigurationRules.ItemDefinition> items = completeSurveyItems();
        items.removeIf(item -> category.equals(item.businessCategoryCode()));
        return items;
    }

    static List<CutoverConfigurationRules.BindingRule> surveyRules() {
        return CORE_SURVEY_CATEGORIES_IN_ORDER.stream()
                .map(category -> new CutoverConfigurationRules.BindingRule("RULE_SURVEY_" + category,
                        "SURVEY_" + category,
                        conditions(Map.of("CUTOVER_LEVEL", List.of("A", "B", "C"))),
                        10, true, true))
                .toList();
    }

    static List<CutoverConfigurationRules.BindingRule> surveyRule(Boolean requiredResult) {
        return List.of(new CutoverConfigurationRules.BindingRule("RULE_SURVEY_BACKGROUND",
                "SURVEY_CUTOVER_BACKGROUND", conditions(Map.of("CUTOVER_LEVEL", List.of("A", "B", "C"))),
                10, requiredResult, true));
    }

    static CutoverConfigurationRules.BindingRule rule(String stableItemKey, String networkMode,
                                                       Boolean requiredResult) {
        return new CutoverConfigurationRules.BindingRule("RULE_" + stableItemKey + "_" + networkMode,
                stableItemKey, conditions(Map.of("NETWORK_MODE", List.of(networkMode))),
                10, requiredResult, true);
    }

    static CutoverMatrixValidationContext context() {
        return new CutoverMatrixValidationContext(
                java.util.Set.of("VERSION_UPGRADE", "CONFIGURATION_CHANGE"),
                CutoverRiskMatrixRules.DUAL_COUNTS.keySet(),
                java.util.Set.of("FW", "SW", "ADX"),
                java.util.Set.of("A", "B", "C", "D"));
    }

    private static CutoverConfigurationRules.ItemDefinition item(String key, String type, String category,
                                                                  String subtableCode,
                                                                  Map<String, Object> interfaceSchema) {
        return new CutoverConfigurationRules.ItemDefinition(key, type, category, key,
                "TABLE", interfaceSchema, "BOOLEAN_REMARK", true,
                "MANUAL", null, subtableCode, true);
    }

    private static Map<String, Object> backgroundSchema() {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("solvesOnlineIssue", null));
        fields.add(field("issueTicketNo", visibleWhen("solvesOnlineIssue")));
        fields.add(field("issueHandler", visibleWhen("solvesOnlineIssue")));
        fields.add(field("repeatCutover", null));
        fields.add(field("firstCutoverOwner", visibleWhen("repeatCutover")));
        fields.add(field("backgroundDescription", null));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("fields", fields);
        return schema;
    }

    private static Map<String, Object> field(String code, Map<String, Object> visibleWhen) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("code", code);
        if (visibleWhen != null) {
            field.put("visibleWhen", visibleWhen);
        }
        return field;
    }

    private static Map<String, Object> visibleWhen(String field) {
        return Map.of("field", field, "equals", true);
    }

    private static String conditions(Map<String, List<String>> conditions) {
        return conditions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "\"" + entry.getKey() + "\":[" + entry.getValue().stream()
                        .sorted()
                        .map(value -> "\"" + value + "\"")
                        .collect(java.util.stream.Collectors.joining(",")) + "]")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private static final List<String> CORE_SURVEY_CATEGORIES_IN_ORDER = List.of(
            "CUTOVER_BACKGROUND", "BUSINESS_SUMMARY", "IMPACT_SCOPE",
            "CONTINUITY_REQUIREMENT", "INTERRUPTION_COUNT", "CURRENT_TOPOLOGY",
            "DEVICE_LOCATION_PLAN", "INTERFACE_INTERCONNECT_PLAN", "IP_VLAN_PLAN",
            "PERFORMANCE_BASELINE", "CONNECTIVITY_TEST_CASE", "VENDOR_CONFIG_TRANSLATION");
}
