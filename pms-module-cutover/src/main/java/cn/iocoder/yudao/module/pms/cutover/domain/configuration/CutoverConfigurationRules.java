package cn.iocoder.yudao.module.pms.cutover.domain.configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CUT-07 配置聚合的纯领域校验规则。
 */
public final class CutoverConfigurationRules {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DISABLED = "DISABLED";

    private static final Set<String> ITEM_TYPES = Set.of("BUSINESS_SURVEY", "RISK", "DUAL_MACHINE_CHECK");
    private static final Set<String> REQUIRED_DIMENSIONS = Set.of(
            "CUTOVER_TYPE", "NETWORK_MODE", "DEVICE_TYPE", "CUTOVER_LEVEL");

    private CutoverConfigurationRules() {
    }

    public static boolean isEditable(String status) {
        return STATUS_DRAFT.equals(status);
    }

    public static List<ValidationError> validate(List<DimensionDefinition> dimensions,
                                                  List<ItemDefinition> items,
                                                  List<BindingRule> rules,
                                                  List<PlanTemplateSection> sections) {
        List<ValidationError> errors = new ArrayList<>();
        validateDimensions(dimensions, errors);
        Set<String> itemKeys = validateItems(items, errors);
        validateRules(rules, itemKeys, errors);
        validateSections(sections, errors);
        return List.copyOf(errors);
    }

    private static void validateDimensions(List<DimensionDefinition> dimensions, List<ValidationError> errors) {
        Set<String> codes = new HashSet<>();
        Set<String> enabledCodes = new HashSet<>();
        for (int i = 0; i < safe(dimensions).size(); i++) {
            DimensionDefinition dimension = safe(dimensions).get(i);
            String location = "dimensions[" + i + "]";
            if (blank(dimension.code()) || !codes.add(dimension.code())) {
                errors.add(new ValidationError(location + ".code", "维度编码不能为空且不能重复"));
            }
            if (dimension.enabled() && !blank(dimension.code())) {
                enabledCodes.add(dimension.code());
            }
            if (blank(dimension.name()) || blank(dimension.dataType()) || blank(dimension.valueSource())
                    || blank(dimension.owner()) || blank(dimension.contextPath())) {
                errors.add(new ValidationError(location, "维度名称、数据类型、允许值来源、Owner和上下文路径必须完整"));
            }
        }
        for (String required : REQUIRED_DIMENSIONS) {
            if (!enabledCodes.contains(required)) {
                errors.add(new ValidationError("dimensions", "缺少或未启用V1必需维度：" + required));
            }
        }
    }

    private static Set<String> validateItems(List<ItemDefinition> items, List<ValidationError> errors) {
        Set<String> allKeys = new HashSet<>();
        Set<String> enabledKeys = new HashSet<>();
        for (int i = 0; i < safe(items).size(); i++) {
            ItemDefinition item = safe(items).get(i);
            String location = "items[" + i + "]";
            if (blank(item.stableItemKey()) || !allKeys.add(item.stableItemKey())) {
                errors.add(new ValidationError(location + ".stableItemKey", "稳定项键不能为空且不能重复"));
            }
            if (item.enabled() && !blank(item.stableItemKey())) {
                enabledKeys.add(item.stableItemKey());
            }
            if (!ITEM_TYPES.contains(item.itemType())) {
                errors.add(new ValidationError(location + ".itemType", "采集项类型不受支持"));
            }
            if (blank(item.businessCategoryCode())) {
                errors.add(new ValidationError(location + ".businessCategoryCode", "业务分类码不能为空"));
            }
            if (item.interfaceSchema() == null) {
                errors.add(new ValidationError(location + ".interfaceSchema", "界面Schema不能为空"));
            }
            if (blank(item.itemName()) || blank(item.interfaceFormat()) || blank(item.feedbackFormat())) {
                errors.add(new ValidationError(location, "项命名、界面格式和反馈格式必须完整"));
            }
            if ("DUAL_MACHINE_CHECK".equals(item.itemType()) && blank(item.subtableCode())) {
                errors.add(new ValidationError(location + ".subtableCode", "双机部署检查项必须指定所属子表"));
            }
            if (!"DUAL_MACHINE_CHECK".equals(item.itemType()) && !blank(item.subtableCode())) {
                errors.add(new ValidationError(location + ".subtableCode", "非双机部署检查项不得指定所属子表"));
            }
            if ("EXTERNAL".equals(item.workMode()) && blank(item.externalSourceConfig())) {
                errors.add(new ValidationError(location + ".externalSourceConfig", "外部工作方式必须配置数据来源"));
            }
        }
        return enabledKeys;
    }

    private static void validateRules(List<BindingRule> rules, Set<String> itemKeys,
                                      List<ValidationError> errors) {
        Set<String> ruleKeys = new HashSet<>();
        Set<String> decisions = new HashSet<>();
        for (int i = 0; i < safe(rules).size(); i++) {
            BindingRule rule = safe(rules).get(i);
            String location = "bindingRules[" + i + "]";
            if (blank(rule.stableRuleKey()) || !ruleKeys.add(rule.stableRuleKey())) {
                errors.add(new ValidationError(location + ".stableRuleKey", "稳定规则键不能为空且不能重复"));
            }
            if (!rule.enabled()) {
                continue;
            }
            if (!itemKeys.contains(rule.stableItemKey())) {
                errors.add(new ValidationError(location + ".stableItemKey", "绑定规则引用的采集项不存在"));
            }
            if (blank(rule.dimensionConditionSnapshot())) {
                errors.add(new ValidationError(location + ".dimensionConditionSnapshot", "维度条件不能为空"));
            }
            if (rule.requiredResult() == null) {
                errors.add(new ValidationError(location + ".requiredResult", "绑定级必填结果不能为空"));
            }
            String decision = rule.stableItemKey() + "|" + rule.dimensionConditionSnapshot() + "|" + rule.priority();
            if (!decisions.add(decision)) {
                errors.add(new ValidationError(location, "同一采集项存在条件和优先级完全相同的冲突规则"));
            }
        }
    }

    private static void validateSections(List<PlanTemplateSection> sections, List<ValidationError> errors) {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < safe(sections).size(); i++) {
            PlanTemplateSection section = safe(sections).get(i);
            String location = "planTemplateSections[" + i + "]";
            if (blank(section.stableSectionKey()) || !keys.add(section.stableSectionKey())) {
                errors.add(new ValidationError(location + ".stableSectionKey", "稳定章节键不能为空且不能重复"));
            }
            if (blank(section.title())) {
                errors.add(new ValidationError(location + ".title", "章节标题不能为空"));
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safe(List<T> value) {
        return value == null ? List.of() : value;
    }

    public record DimensionDefinition(String code, String name, String dataType, String valueSource,
                                      String owner, String contextPath, boolean enabled) {
    }

    public record ItemDefinition(String stableItemKey, String itemType, String businessCategoryCode,
                                 String itemName, String interfaceFormat, Map<String, Object> interfaceSchema,
                                 String feedbackFormat, boolean required,
                                 String workMode, String externalSourceConfig, String subtableCode,
                                 boolean enabled) {

        public ItemDefinition(String stableItemKey, String itemType, String itemName,
                              String interfaceFormat, String feedbackFormat, boolean required,
                              String workMode, String externalSourceConfig, String subtableCode,
                              boolean enabled) {
            this(stableItemKey, itemType, null, itemName, interfaceFormat, null, feedbackFormat,
                    required, workMode, externalSourceConfig, subtableCode, enabled);
        }
    }

    public record BindingRule(String stableRuleKey, String stableItemKey,
                              String dimensionConditionSnapshot, int priority,
                              Boolean requiredResult, boolean enabled) {

        public BindingRule(String stableRuleKey, String stableItemKey,
                           String dimensionConditionSnapshot, int priority, boolean enabled) {
            this(stableRuleKey, stableItemKey, dimensionConditionSnapshot, priority, null, enabled);
        }
    }

    public record PlanTemplateSection(String stableSectionKey, String title, int sortOrder,
                                      List<String> cutoverTypeCodes, List<String> levelCodes,
                                      boolean required) {
    }

    public record ValidationError(String location, String message) {
    }
}
