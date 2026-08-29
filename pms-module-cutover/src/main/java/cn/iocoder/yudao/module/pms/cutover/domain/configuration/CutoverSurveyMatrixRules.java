package cn.iocoder.yudao.module.pms.cutover.domain.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.cutover.domain.configuration.CutoverConfigurationRules.ValidationError;

/**
 * CUT-10 调研矩阵发布规则。
 */
public final class CutoverSurveyMatrixRules {

    public static final Set<String> CORE_SURVEY_CATEGORIES = Set.of(
            "CUTOVER_BACKGROUND", "BUSINESS_SUMMARY", "IMPACT_SCOPE",
            "CONTINUITY_REQUIREMENT", "INTERRUPTION_COUNT", "CURRENT_TOPOLOGY",
            "DEVICE_LOCATION_PLAN", "INTERFACE_INTERCONNECT_PLAN", "IP_VLAN_PLAN",
            "PERFORMANCE_BASELINE", "CONNECTIVITY_TEST_CASE", "VENDOR_CONFIG_TRANSLATION");

    public static final Set<String> BACKGROUND_FIELDS = Set.of(
            "solvesOnlineIssue", "issueTicketNo", "issueHandler",
            "repeatCutover", "firstCutoverOwner", "backgroundDescription");

    private static final Map<String, String> BACKGROUND_DEPENDENCIES = Map.of(
            "issueTicketNo", "solvesOnlineIssue",
            "issueHandler", "solvesOnlineIssue",
            "firstCutoverOwner", "repeatCutover");

    private CutoverSurveyMatrixRules() {
    }

    public static List<ValidationError> validate(List<CutoverConfigurationRules.ItemDefinition> items,
                                                 List<CutoverConfigurationRules.BindingRule> rules,
                                                 CutoverMatrixValidationContext context) {
        List<ValidationError> errors = new ArrayList<>();
        List<CutoverConfigurationRules.ItemDefinition> surveyItems = safe(items).stream()
                .filter(CutoverConfigurationRules.ItemDefinition::enabled)
                .filter(item -> "BUSINESS_SURVEY".equals(item.itemType()))
                .toList();
        validateEnabledItemsHaveBinding(surveyItems, safe(rules), errors);
        validateCoreCategories(surveyItems, errors);
        validateBackgroundSchema(surveyItems, errors);
        validateBindings(surveyItems, safe(rules), errors);
        return List.copyOf(errors);
    }

    private static void validateEnabledItemsHaveBinding(
            List<CutoverConfigurationRules.ItemDefinition> items,
            List<CutoverConfigurationRules.BindingRule> rules,
            List<ValidationError> errors) {
        Set<String> boundItemKeys = rules.stream()
                .filter(CutoverConfigurationRules.BindingRule::enabled)
                .map(CutoverConfigurationRules.BindingRule::stableItemKey)
                .collect(java.util.stream.Collectors.toSet());
        items.stream()
                .filter(item -> !boundItemKeys.contains(item.stableItemKey()))
                .forEach(item -> errors.add(new ValidationError(
                        "items." + item.stableItemKey() + ".bindingRules",
                        "启用调研项必须至少配置一条启用绑定：" + item.stableItemKey())));
    }

    private static void validateCoreCategories(List<CutoverConfigurationRules.ItemDefinition> items,
                                               List<ValidationError> errors) {
        Set<String> categories = items.stream()
                .map(CutoverConfigurationRules.ItemDefinition::businessCategoryCode)
                .filter(category -> category != null && !category.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        CORE_SURVEY_CATEGORIES.stream().sorted()
                .filter(category -> !categories.contains(category))
                .forEach(category -> errors.add(new ValidationError("categories." + category,
                        "缺少核心调研类别：" + category)));
    }

    private static void validateBackgroundSchema(List<CutoverConfigurationRules.ItemDefinition> items,
                                                 List<ValidationError> errors) {
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            CutoverConfigurationRules.ItemDefinition item = items.get(itemIndex);
            if (!"CUTOVER_BACKGROUND".equals(item.businessCategoryCode())) {
                continue;
            }
            String location = "items[" + itemIndex + "].interfaceSchema";
            Map<String, Object> schema = item.interfaceSchema();
            if (schema == null) {
                errors.add(new ValidationError(location, "割接背景必须定义六个字段及条件显示关系"));
                continue;
            }
            Map<String, Map<String, Object>> fields = fields(schema);
            BACKGROUND_FIELDS.stream().sorted()
                    .filter(code -> !fields.containsKey(code))
                    .forEach(code -> errors.add(new ValidationError(location + ".fields",
                            "割接背景缺少字段：" + code)));

            for (Map.Entry<String, String> dependency : BACKGROUND_DEPENDENCIES.entrySet()) {
                Map<String, Object> field = fields.get(dependency.getKey());
                if (field == null) {
                    continue;
                }
                Object visibleWhenValue = field.get("visibleWhen");
                Map<?, ?> visibleWhen = visibleWhenValue instanceof Map<?, ?> map ? map : Map.of();
                if (!dependency.getValue().equals(visibleWhen.get("field"))
                        || !Boolean.TRUE.equals(visibleWhen.get("equals"))) {
                    errors.add(new ValidationError(location + ".fields." + dependency.getKey() + ".visibleWhen",
                            dependency.getKey() + "必须依赖" + dependency.getValue() + " == true"));
                }
            }
            validateConditionReferences(schema, fields.keySet(), location, errors);
        }
    }

    private static void validateBindings(List<CutoverConfigurationRules.ItemDefinition> items,
                                         List<CutoverConfigurationRules.BindingRule> rules,
                                         List<ValidationError> errors) {
        Set<String> itemKeys = items.stream()
                .map(CutoverConfigurationRules.ItemDefinition::stableItemKey)
                .collect(java.util.stream.Collectors.toSet());
        List<IndexedRule> enabledRules = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            CutoverConfigurationRules.BindingRule rule = rules.get(index);
            if (!rule.enabled() || !itemKeys.contains(rule.stableItemKey())) {
                continue;
            }
            if (rule.requiredResult() == null) {
                errors.add(new ValidationError("bindingRules[" + index + "].requiredResult",
                        "调研绑定的必填结果不能为空"));
            }
            enabledRules.add(new IndexedRule(index, rule, CutoverMatrixRuleSupport.conditions(rule)));
        }

        Set<String> conflictReported = new HashSet<>();
        for (int leftIndex = 0; leftIndex < enabledRules.size(); leftIndex++) {
            IndexedRule left = enabledRules.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < enabledRules.size(); rightIndex++) {
                IndexedRule right = enabledRules.get(rightIndex);
                if (!left.rule().stableItemKey().equals(right.rule().stableItemKey())) {
                    continue;
                }
                if (left.rule().priority() == right.rule().priority()
                        && left.conditions().equals(right.conditions())
                        && left.rule().requiredResult() != null && right.rule().requiredResult() != null
                        && !left.rule().requiredResult().equals(right.rule().requiredResult())) {
                    String conflictKey = left.rule().stableItemKey() + "|" + left.rule().priority()
                            + "|" + left.conditions();
                    if (conflictReported.add(conflictKey)) {
                        errors.add(new ValidationError("bindingRules[" + right.index() + "]",
                                "同条件同优先级的调研绑定存在必填结果冲突"));
                    }
                }
                validateSpecificPriority(left, right, errors);
                validateSpecificPriority(right, left, errors);
            }
        }
    }

    private static void validateSpecificPriority(IndexedRule specific, IndexedRule broader,
                                                 List<ValidationError> errors) {
        if (CutoverMatrixRuleSupport.isMoreSpecific(specific.conditions(), broader.conditions())
                && specific.rule().priority() <= broader.rule().priority()) {
            errors.add(new ValidationError("bindingRules[" + specific.index() + "].priority",
                    "具体组合优先级必须高于通配组合"));
        }
    }

    private static Map<String, Map<String, Object>> fields(Map<String, Object> schema) {
        Object fieldsValue = schema.get("fields");
        if (!(fieldsValue instanceof List<?> values)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> fields = new HashMap<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> rawField) || !(rawField.get("code") instanceof String code)
                    || code.isBlank()) {
                continue;
            }
            Map<String, Object> field = new HashMap<>();
            rawField.forEach((key, fieldValue) -> field.put(String.valueOf(key), fieldValue));
            fields.put(code, field);
        }
        return fields;
    }

    private static void validateConditionReferences(Object value, Set<String> fieldCodes, String location,
                                                    List<ValidationError> errors) {
        if (value instanceof Map<?, ?> map) {
            Object directReference = map.get("visibleWhenField");
            if (directReference instanceof String reference && !fieldCodes.contains(reference)) {
                errors.add(new ValidationError(location + ".visibleWhenField",
                        "条件字段不存在：" + reference));
            }
            Object visibleWhenValue = map.get("visibleWhen");
            if (visibleWhenValue instanceof Map<?, ?> visibleWhen
                    && visibleWhen.get("field") instanceof String reference
                    && !fieldCodes.contains(reference)) {
                errors.add(new ValidationError(location + ".visibleWhen.field",
                        "条件字段不存在：" + reference));
            }
            map.values().forEach(child -> validateConditionReferences(child, fieldCodes, location, errors));
        } else if (value instanceof Iterable<?> iterable) {
            iterable.forEach(child -> validateConditionReferences(child, fieldCodes, location, errors));
        }
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record IndexedRule(int index, CutoverConfigurationRules.BindingRule rule,
                               Map<String, Set<String>> conditions) {
    }
}
