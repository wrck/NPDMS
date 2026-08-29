package cn.iocoder.yudao.module.pms.cutover.domain.configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.cutover.domain.configuration.CutoverConfigurationRules.ValidationError;

/**
 * CUT-09 风险矩阵发布规则。
 */
public final class CutoverRiskMatrixRules {

    public static final Map<String, Integer> DUAL_COUNTS = Map.of(
            "VSM", 17,
            "SILENT_DUAL", 25,
            "DRP_DUAL", 23,
            "NORMAL_DUAL", 24,
            "CLUSTER", 8);

    public static final Set<String> REQUIRED_RISK_CATEGORIES = Set.of(
            "CURRENT_VERSION_BULLETIN", "TARGET_VERSION_BULLETIN",
            "DUAL_CONFIG_CONSISTENCY", "FILTER_NAT_QOS_COMPILE_COUNT",
            "COMPILE_LIMIT_ASSESSMENT", "SESSION_SYNC", "DUAL_CONTROLLER_VERSION",
            "PACKAGE_MD5", "MAJOR_PROJECT_SPARES", "SYSTEM_LOG", "DIAGNOSTIC_LOG",
            "RUNNING_VERSION_BACKUP", "HOT_PATCH_BACKUP", "LICENSE_BACKUP",
            "CONFIG_BACKUP", "DYNAMIC_TABLE_COLLECTION", "MTU_JUMBO_FRAME",
            "HUNDRED_G_FEC", "LONG_CONNECTION", "SECOND_PASS_DEVICE", "STP",
            "F5_DEFAULT", "ADWARE_DEFAULT", "ROOM_OPERATION_COMMITMENT");

    public static final Set<String> ALL_SITUATION_REQUIRED = Set.of(
            "CURRENT_VERSION_BULLETIN", "SYSTEM_LOG", "DIAGNOSTIC_LOG",
            "RUNNING_VERSION_BACKUP", "HOT_PATCH_BACKUP", "LICENSE_BACKUP",
            "CONFIG_BACKUP", "ROOM_OPERATION_COMMITMENT");

    private static final Map<String, String> DUAL_LABELS = Map.of(
            "VSM", "VSM双机",
            "SILENT_DUAL", "静默双机",
            "DRP_DUAL", "DRP双机",
            "NORMAL_DUAL", "普通双机",
            "CLUSTER", "集群");

    private CutoverRiskMatrixRules() {
    }

    public static List<ValidationError> validate(List<CutoverConfigurationRules.ItemDefinition> items,
                                                 List<CutoverConfigurationRules.BindingRule> rules,
                                                 CutoverMatrixValidationContext context) {
        List<ValidationError> errors = new ArrayList<>();
        List<CutoverConfigurationRules.ItemDefinition> enabledItems = safe(items).stream()
                .filter(CutoverConfigurationRules.ItemDefinition::enabled)
                .toList();
        List<CutoverConfigurationRules.BindingRule> enabledRules = safe(rules).stream()
                .filter(CutoverConfigurationRules.BindingRule::enabled)
                .toList();

        validateEnabledItemsHaveBinding(enabledItems, enabledRules, errors);
        validateRequiredCategories(enabledItems, errors);
        validateDualMachineCounts(enabledItems, errors);
        validateDedicatedConditions(enabledItems, enabledRules, errors);
        validateAllSituationCoverage(enabledItems, enabledRules, safe(context), errors);
        return List.copyOf(errors);
    }

    private static void validateEnabledItemsHaveBinding(
            List<CutoverConfigurationRules.ItemDefinition> items,
            List<CutoverConfigurationRules.BindingRule> rules,
            List<ValidationError> errors) {
        Set<String> boundItemKeys = rules.stream()
                .map(CutoverConfigurationRules.BindingRule::stableItemKey)
                .collect(java.util.stream.Collectors.toSet());
        items.stream()
                .filter(item -> "RISK".equals(item.itemType())
                        || "DUAL_MACHINE_CHECK".equals(item.itemType()))
                .filter(item -> !boundItemKeys.contains(item.stableItemKey()))
                .forEach(item -> errors.add(new ValidationError(
                        "items." + item.stableItemKey() + ".bindingRules",
                        "启用风险或双机项必须至少配置一条启用绑定：" + item.stableItemKey())));
    }

    private static void validateRequiredCategories(List<CutoverConfigurationRules.ItemDefinition> items,
                                                   List<ValidationError> errors) {
        Set<String> categories = items.stream()
                .filter(item -> "RISK".equals(item.itemType()))
                .map(CutoverConfigurationRules.ItemDefinition::businessCategoryCode)
                .filter(category -> category != null && !category.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        REQUIRED_RISK_CATEGORIES.stream().sorted()
                .filter(category -> !categories.contains(category))
                .forEach(category -> errors.add(new ValidationError("categories." + category,
                        "缺少普通风险基准类别：" + category)));
    }

    private static void validateDualMachineCounts(List<CutoverConfigurationRules.ItemDefinition> items,
                                                  List<ValidationError> errors) {
        Map<String, Long> actual = items.stream()
                .filter(item -> "DUAL_MACHINE_CHECK".equals(item.itemType()))
                .filter(item -> item.subtableCode() != null && !item.subtableCode().isBlank())
                .collect(java.util.stream.Collectors.groupingBy(
                        CutoverConfigurationRules.ItemDefinition::subtableCode,
                        LinkedHashMap::new, java.util.stream.Collectors.counting()));
        long missingSubtableCount = items.stream()
                .filter(item -> "DUAL_MACHINE_CHECK".equals(item.itemType()))
                .filter(item -> item.subtableCode() == null || item.subtableCode().isBlank())
                .count();
        if (missingSubtableCount > 0) {
            errors.add(new ValidationError("dualCounts.subtableCode",
                    "双机检查项必须指定所属组网模式，当前缺失" + missingSubtableCount + "项"));
        }
        DUAL_COUNTS.forEach((mode, expected) -> {
            long count = actual.getOrDefault(mode, 0L);
            if (count != expected) {
                errors.add(new ValidationError("dualCounts." + mode,
                        DUAL_LABELS.get(mode) + "应为" + expected + "项，当前" + count + "项"));
            }
        });
        actual.keySet().stream().filter(mode -> !DUAL_COUNTS.containsKey(mode))
                .forEach(mode -> errors.add(new ValidationError("dualCounts." + mode,
                        "双机检查项所属组网模式不在五类基准中：" + mode)));
    }

    private static void validateDedicatedConditions(List<CutoverConfigurationRules.ItemDefinition> items,
                                                    List<CutoverConfigurationRules.BindingRule> rules,
                                                    List<ValidationError> errors) {
        Map<String, CutoverConfigurationRules.ItemDefinition> itemByKey = items.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CutoverConfigurationRules.ItemDefinition::stableItemKey, item -> item, (left, right) -> left));
        for (int index = 0; index < rules.size(); index++) {
            CutoverConfigurationRules.BindingRule rule = rules.get(index);
            CutoverConfigurationRules.ItemDefinition item = itemByKey.get(rule.stableItemKey());
            if (item == null) {
                continue;
            }
            Map<String, Set<String>> conditions = CutoverMatrixRuleSupport.conditions(rule);
            if ("TARGET_VERSION_BULLETIN".equals(item.businessCategoryCode())) {
                Set<String> cutoverTypes = conditions.getOrDefault("CUTOVER_TYPE", Set.of());
                if (!Set.of("VERSION_UPGRADE").equals(cutoverTypes)) {
                    errors.add(new ValidationError("bindingRules[" + index + "].dimensionConditionSnapshot",
                            "升级后版本公告仅适用于版本升级"));
                }
            }
            if ("DUAL_MACHINE_CHECK".equals(item.itemType())) {
                Set<String> modes = conditions.getOrDefault("NETWORK_MODE", Set.of());
                if (item.subtableCode() == null || item.subtableCode().isBlank()
                        || modes.size() != 1 || !modes.contains(item.subtableCode())) {
                    errors.add(new ValidationError("bindingRules[" + index + "].dimensionConditionSnapshot",
                            "双机检查规则不得跨所属组网模式：" + item.subtableCode()));
                }
            }
        }
    }

    private static void validateAllSituationCoverage(List<CutoverConfigurationRules.ItemDefinition> items,
                                                     List<CutoverConfigurationRules.BindingRule> rules,
                                                     CutoverMatrixValidationContext context,
                                                     List<ValidationError> errors) {
        Map<String, Set<String>> itemKeysByCategory = items.stream()
                .filter(item -> "RISK".equals(item.itemType()))
                .collect(java.util.stream.Collectors.groupingBy(
                        CutoverConfigurationRules.ItemDefinition::businessCategoryCode,
                        java.util.stream.Collectors.mapping(CutoverConfigurationRules.ItemDefinition::stableItemKey,
                                java.util.stream.Collectors.toSet())));
        Map<String, List<Map<String, Set<String>>>> conditionsByItem = rules.stream()
                .filter(rule -> Boolean.TRUE.equals(rule.requiredResult()))
                .collect(java.util.stream.Collectors.groupingBy(
                        CutoverConfigurationRules.BindingRule::stableItemKey,
                        java.util.stream.Collectors.mapping(CutoverMatrixRuleSupport::conditions,
                                java.util.stream.Collectors.toList())));

        for (String category : ALL_SITUATION_REQUIRED) {
            Set<String> itemKeys = itemKeysByCategory.getOrDefault(category, Set.of());
            for (String cutoverType : context.cutoverTypeCodes()) {
                for (String deviceType : context.deviceTypeCodes()) {
                    for (String level : List.of("A", "B", "C")) {
                        boolean covered = itemKeys.stream()
                                .flatMap(itemKey -> conditionsByItem.getOrDefault(itemKey, List.of()).stream())
                                .anyMatch(condition -> contains(condition, "CUTOVER_TYPE", cutoverType)
                                        && contains(condition, "DEVICE_TYPE", deviceType)
                                        && contains(condition, "CUTOVER_LEVEL", level));
                        if (!covered) {
                            errors.add(new ValidationError("coverage." + category + "." + cutoverType + "."
                                    + deviceType + "." + level,
                                    "所有情况必选项" + category + "缺少显式覆盖："
                                            + cutoverType + "/" + deviceType + "/" + level));
                        }
                    }
                }
            }
        }
    }

    private static boolean contains(Map<String, Set<String>> conditions, String dimension, String value) {
        Set<String> values = conditions.get(dimension);
        return values != null && !values.isEmpty() && values.contains(value);
    }

    private static CutoverMatrixValidationContext safe(CutoverMatrixValidationContext context) {
        return context == null ? new CutoverMatrixValidationContext(Set.of(), Set.of(), Set.of(), Set.of()) : context;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
