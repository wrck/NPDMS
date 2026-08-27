package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** PRE-04需求分析WorkBinding的封闭V1结构与发布快照规则。 */
public final class RequirementAnalysisWorkBindingSchema {

    public static final String BINDING_TYPE = "BUSINESS_OBJECT";
    public static final String TARGET_CONTEXT = "SOL";
    public static final String TARGET_OBJECT_TYPE = "REQUIREMENT_ANALYSIS";
    public static final String TARGET_OBJECT_KEY = "PRE_04_REQUIREMENT_ANALYSIS";
    public static final String CATALOG_CODE = "PRE_04_REQUIREMENT_ANALYSIS";
    public static final int CATALOG_VERSION = 1;

    private static final Set<String> ROOT_KEYS = Set.of(
            "schemaVersion", "catalogCode", "catalogVersion", "extensionItems");
    private static final Set<String> COMMON_ITEM_KEYS = Set.of(
            "fieldCode", "fieldName", "fieldTypeCode", "required", "sortOrder");
    private static final Set<String> SELECTION_ITEM_KEYS = Set.of(
            "fieldCode", "fieldName", "fieldTypeCode", "required", "dictionaryType",
            "optionSnapshot", "sortOrder");
    private static final Set<String> OPTION_KEYS = Set.of("code", "label");
    private static final Set<String> FIELD_TYPES = Set.of(
            "RICH_TEXT", "TEXT", "NUMBER", "BOOLEAN", "SINGLE_SELECT", "MULTI_SELECT");
    private static final Set<String> SELECTION_FIELD_TYPES = Set.of("SINGLE_SELECT", "MULTI_SELECT");
    private static final Set<String> CORE_SECTION_CODES = Set.of(
            "PROJECT_BACKGROUND", "PROJECT_OBJECTIVE", "NETWORK_TOPOLOGY",
            "TRANSMISSION_REQUIREMENT", "TRAFFIC_REQUIREMENT", "BUSINESS_REQUIREMENT",
            "IP_PLANNING", "REDUNDANCY_REQUIREMENT", "SECURITY_PROTECTION",
            "OPERATIONS_REQUIREMENT", "LOGGING_REQUIREMENT");

    private RequirementAnalysisWorkBindingSchema() {
    }

    public static boolean isRequirementAnalysisBinding(TemplateDefinitionContent.TaskDef task) {
        return task != null
                && BINDING_TYPE.equals(task.getWorkBindingTypeCode())
                && TARGET_CONTEXT.equals(task.getTargetContextCode())
                && TARGET_OBJECT_TYPE.equals(task.getTargetObjectType())
                && TARGET_OBJECT_KEY.equals(task.getTargetObjectKey());
    }

    /** 发布时校验选择字典并生成只含当前启用code/label的规范快照。 */
    public static String freezeAndValidate(String bindingJson, DictionarySnapshotProvider dictionaryProvider) {
        Map<?, ?> binding = requireObject(bindingJson);
        requireExactKeys(binding, ROOT_KEYS, "PRE-04绑定配置字段不符合V1契约");
        requireInteger(binding, "schemaVersion", 1);
        if (!CATALOG_CODE.equals(requireString(binding, "catalogCode"))) {
            throw new IllegalArgumentException("PRE-04目录编码无效");
        }
        requireInteger(binding, "catalogVersion", CATALOG_VERSION);
        List<?> items = requireList(binding.get("extensionItems"), "PRE-04扩展项配置缺失");
        List<Map<String, Object>> frozenItems = validateAndFreezeItems(items, dictionaryProvider);
        return JsonUtils.toJsonString(rootSnapshot(frozenItems));
    }

    /** 运行时只校验项目执行契约中已经冻结的快照，不回读SYSTEM字典。 */
    public static ParsedBinding parseFrozen(String bindingJson) {
        Map<?, ?> binding = requireObject(bindingJson);
        requireExactKeys(binding, ROOT_KEYS, "PRE-04冻结配置字段不符合V1契约");
        requireInteger(binding, "schemaVersion", 1);
        if (!CATALOG_CODE.equals(requireString(binding, "catalogCode"))) {
            throw new IllegalArgumentException("PRE-04冻结目录编码无效");
        }
        requireInteger(binding, "catalogVersion", CATALOG_VERSION);
        List<?> items = requireList(binding.get("extensionItems"), "PRE-04冻结扩展项缺失");
        List<Map<String, Object>> frozenItems = validateAndFreezeItems(items, (dictionaryType, options) -> options);
        return new ParsedBinding(CATALOG_CODE, CATALOG_VERSION, JsonUtils.toJsonString(frozenItems));
    }

    private static List<Map<String, Object>> validateAndFreezeItems(
            List<?> items, DictionarySnapshotProvider dictionaryProvider) {
        if (dictionaryProvider == null) {
            throw new IllegalArgumentException("PRE-04字典事实Provider不可用");
        }
        Set<String> codes = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        List<Map<String, Object>> frozen = new ArrayList<>();
        for (Object value : items) {
            Map<?, ?> item = requireMap(value, "PRE-04扩展项无效");
            String fieldType = requireString(item, "fieldTypeCode");
            boolean selection = SELECTION_FIELD_TYPES.contains(fieldType);
            requireExactKeys(item, selection ? SELECTION_ITEM_KEYS : COMMON_ITEM_KEYS,
                    "PRE-04扩展项字段不符合V1契约");
            if (!FIELD_TYPES.contains(fieldType)) {
                throw new IllegalArgumentException("PRE-04扩展项字段类型无效");
            }
            String fieldCode = requireString(item, "fieldCode");
            if (fieldCode.length() > 64 || CORE_SECTION_CODES.contains(fieldCode) || !codes.add(fieldCode)) {
                throw new IllegalArgumentException("PRE-04扩展项编码重复或占用核心编码");
            }
            String fieldName = requireString(item, "fieldName");
            if (fieldName.length() > 128) {
                throw new IllegalArgumentException("PRE-04扩展项名称过长");
            }
            boolean required = requireBoolean(item, "required");
            int sortOrder = requireNonNegativeInteger(item, "sortOrder");
            if (!sortOrders.add(sortOrder)) {
                throw new IllegalArgumentException("PRE-04扩展项排序重复");
            }

            Map<String, Object> frozenItem = new LinkedHashMap<>();
            frozenItem.put("fieldCode", fieldCode);
            frozenItem.put("fieldName", fieldName);
            frozenItem.put("fieldTypeCode", fieldType);
            frozenItem.put("required", required);
            if (selection) {
                String dictionaryType = requireString(item, "dictionaryType");
                if (dictionaryType.length() > 100) {
                    throw new IllegalArgumentException("PRE-04选择项字典类型过长");
                }
                List<OptionSnapshot> requested = parseOptions(item.get("optionSnapshot"));
                List<OptionSnapshot> resolved = dictionaryProvider.resolve(dictionaryType, requested);
                validateResolvedOptions(requested, resolved);
                frozenItem.put("dictionaryType", dictionaryType);
                frozenItem.put("optionSnapshot", resolved.stream()
                        .sorted(Comparator.comparing(OptionSnapshot::code))
                        .map(option -> {
                            Map<String, Object> snapshot = new LinkedHashMap<>();
                            snapshot.put("code", option.code());
                            snapshot.put("label", option.label());
                            return snapshot;
                        }).toList());
            }
            frozenItem.put("sortOrder", sortOrder);
            frozen.add(frozenItem);
        }
        frozen.sort(Comparator.comparingInt(item -> (Integer) item.get("sortOrder")));
        return frozen;
    }

    private static List<OptionSnapshot> parseOptions(Object value) {
        List<?> options = requireList(value, "PRE-04选择项快照缺失");
        if (options.isEmpty()) {
            throw new IllegalArgumentException("PRE-04选择项快照不能为空");
        }
        Set<String> codes = new HashSet<>();
        List<OptionSnapshot> parsed = new ArrayList<>();
        for (Object raw : options) {
            Map<?, ?> option = requireMap(raw, "PRE-04选择项快照无效");
            requireExactKeys(option, OPTION_KEYS, "PRE-04选择项快照字段无效");
            String code = requireString(option, "code");
            String label = requireString(option, "label");
            if (!codes.add(code)) {
                throw new IllegalArgumentException("PRE-04选择项编码重复");
            }
            parsed.add(new OptionSnapshot(code, label));
        }
        return parsed;
    }

    private static void validateResolvedOptions(List<OptionSnapshot> requested, List<OptionSnapshot> resolved) {
        if (resolved == null || resolved.size() != requested.size()) {
            throw new IllegalArgumentException("PRE-04选择项未命中启用字典");
        }
        Set<String> expectedCodes = new HashSet<>();
        requested.forEach(option -> expectedCodes.add(option.code()));
        Set<String> resolvedCodes = new HashSet<>();
        for (OptionSnapshot option : resolved) {
            if (option == null || option.code() == null || option.code().isBlank()
                    || option.label() == null || option.label().isBlank() || !resolvedCodes.add(option.code())) {
                throw new IllegalArgumentException("PRE-04启用字典快照无效");
            }
        }
        if (!expectedCodes.equals(resolvedCodes)) {
            throw new IllegalArgumentException("PRE-04选择项未命中启用字典");
        }
    }

    private static Map<String, Object> rootSnapshot(List<Map<String, Object>> items) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("catalogCode", CATALOG_CODE);
        root.put("catalogVersion", CATALOG_VERSION);
        root.put("extensionItems", items);
        return root;
    }

    private static Map<?, ?> requireObject(String json) {
        Map<?, ?> value = JsonUtils.parseObjectQuietly(json, Map.class);
        if (value == null) {
            throw new IllegalArgumentException("PRE-04绑定配置不是合法JSON对象");
        }
        return value;
    }

    private static Map<?, ?> requireMap(Object value, String message) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(message);
        }
        return map;
    }

    private static List<?> requireList(Object value, String message) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(message);
        }
        return list;
    }

    private static void requireExactKeys(Map<?, ?> value, Set<String> keys, String message) {
        if (!stringKeys(value).equals(keys)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Set<String> stringKeys(Map<?, ?> value) {
        Set<String> keys = new HashSet<>();
        for (Object key : value.keySet()) {
            if (!(key instanceof String text)) {
                throw new IllegalArgumentException("PRE-04 JSON字段名无效");
            }
            keys.add(text);
        }
        return keys;
    }

    private static String requireString(Map<?, ?> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("PRE-04字段" + key + "无效");
        }
        return text.trim();
    }

    private static boolean requireBoolean(Map<?, ?> value, String key) {
        if (!(value.get(key) instanceof Boolean result)) {
            throw new IllegalArgumentException("PRE-04字段" + key + "无效");
        }
        return result;
    }

    private static void requireInteger(Map<?, ?> value, String key, int expected) {
        if (requireIntegerValue(value, key) != expected) {
            throw new IllegalArgumentException("PRE-04字段" + key + "无效");
        }
    }

    private static int requireNonNegativeInteger(Map<?, ?> value, String key) {
        int number = requireIntegerValue(value, key);
        if (number < 0) {
            throw new IllegalArgumentException("PRE-04字段" + key + "无效");
        }
        return number;
    }

    private static int requireIntegerValue(Map<?, ?> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("PRE-04字段" + key + "无效");
        }
        return number.intValue();
    }

    @FunctionalInterface
    public interface DictionarySnapshotProvider {
        List<OptionSnapshot> resolve(String dictionaryType, List<OptionSnapshot> requestedOptions);
    }

    public record OptionSnapshot(String code, String label) {
    }

    public record ParsedBinding(String catalogCode, Integer catalogVersion, String extensionItemsSnapshot) {
    }
}
