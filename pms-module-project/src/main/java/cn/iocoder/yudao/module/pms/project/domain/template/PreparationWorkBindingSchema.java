package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** PRE-02固定目录与模板WorkBinding的封闭V1结构。 */
public final class PreparationWorkBindingSchema {

    public static final String CONFIG_KEY = "pms.sol.preparation.site-survey.form-catalog.v1";
    public static final String BINDING_TYPE = "BUSINESS_OBJECT";
    public static final String TARGET_CONTEXT = "SOL";
    public static final String TARGET_OBJECT_TYPE = "SITE_SURVEY_PREPARATION";
    public static final String TARGET_OBJECT_KEY = "PRE_02_SITE_SURVEY";

    private static final Set<String> BASELINE_ITEMS = Set.of(
            "POWER", "NETWORK_PORT", "FIBER", "CABINET", "NETWORK_CABLE", "OPTICAL_MODULE");
    private static final Set<String> ROOT_CATALOG_KEYS = Set.of(
            "schemaVersion", "catalogCode", "catalogVersion", "commonFields", "forms");
    private static final Set<String> FIELD_KEYS = Set.of(
            "fieldCode", "fieldType", "required", "maxLength", "options", "sortOrder");
    private static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "NUMBER", "BOOLEAN", "SINGLE_SELECT", "MULTI_SELECT");
    private static final Set<String> FORM_KEYS = Set.of("formCode", "formVersion");
    private static final Set<String> BINDING_KEYS = Set.of(
            "schemaVersion", "preparationTemplateCode", "preparationTemplateRevision",
            "fixedFormCatalogVersion", "itemConfiguration");
    private static final Set<String> ITEM_KEYS = Set.of(
            "itemCode", "itemName", "enabled", "formCode", "formVersion", "evidenceRequired",
            "sourceRequirementCode", "waiverAllowed", "approvalRoleCode", "sortOrder");
    private static final Set<String> SOURCE_REQUIREMENTS = Set.of("NONE", "OA_REQUIRED");
    private static final Set<String> APPROVAL_ROLES = Set.of("SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");

    private PreparationWorkBindingSchema() {
    }

    public static boolean isPreparationBinding(TemplateDefinitionContent.TaskDef task) {
        return task != null
                && BINDING_TYPE.equals(task.getWorkBindingTypeCode())
                && TARGET_CONTEXT.equals(task.getTargetContextCode())
                && TARGET_OBJECT_TYPE.equals(task.getTargetObjectType())
                && TARGET_OBJECT_KEY.equals(task.getTargetObjectKey());
    }

    public static ParsedBinding parseAndValidate(String bindingJson, String catalogJson) {
        Catalog catalog = parseCatalog(catalogJson);
        return parseBinding(bindingJson, catalog);
    }

    /** 运行时只校验已冻结的V1事实，不回读可变配置。 */
    public static ParsedBinding parseFrozen(String bindingJson) {
        Set<FormIdentity> forms = new HashSet<>();
        for (String code : BASELINE_ITEMS) {
            forms.add(new FormIdentity(code, 1));
        }
        return parseBinding(bindingJson, new Catalog(1, Set.copyOf(forms)));
    }

    private static ParsedBinding parseBinding(String bindingJson, Catalog catalog) {
        Map<?, ?> binding = requireObject(bindingJson, "PRE-02绑定配置不是合法JSON对象");
        requireExactKeys(binding, BINDING_KEYS, "PRE-02绑定配置字段不符合V1契约");
        requireInteger(binding, "schemaVersion", 1);
        String templateCode = requireString(binding, "preparationTemplateCode");
        if (!TARGET_OBJECT_KEY.equals(templateCode)) {
            throw new IllegalArgumentException("PRE-02准备模板编码无效");
        }
        int templateRevision = requirePositiveInteger(binding, "preparationTemplateRevision");
        int catalogVersion = requirePositiveInteger(binding, "fixedFormCatalogVersion");
        if (catalogVersion != catalog.version()) {
            throw new IllegalArgumentException("PRE-02固定表单目录版本不匹配");
        }
        List<?> items = requireList(binding.get("itemConfiguration"), "PRE-02工勘项配置缺失");
        validateItems(items, catalog.forms());
        return new ParsedBinding(templateCode, templateRevision, catalogVersion, JsonUtils.toJsonString(items));
    }

    private static Catalog parseCatalog(String catalogJson) {
        Map<?, ?> catalog = requireObject(catalogJson, "PRE-02固定表单目录不存在或不是合法JSON对象");
        requireExactKeys(catalog, ROOT_CATALOG_KEYS, "PRE-02固定表单目录字段不符合V1契约");
        requireInteger(catalog, "schemaVersion", 1);
        if (!TARGET_OBJECT_KEY.equals(requireString(catalog, "catalogCode"))) {
            throw new IllegalArgumentException("PRE-02固定表单目录编码无效");
        }
        int catalogVersion = requirePositiveInteger(catalog, "catalogVersion");
        if (catalogVersion != 1) {
            throw new IllegalArgumentException("PRE-02固定表单目录版本无效");
        }
        validateCommonFields(requireList(catalog.get("commonFields"), "PRE-02固定表单字段缺失"));
        List<?> forms = requireList(catalog.get("forms"), "PRE-02固定表单身份缺失");
        Set<FormIdentity> identities = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (Object value : forms) {
            Map<?, ?> form = requireMap(value, "PRE-02固定表单身份无效");
            requireExactKeys(form, FORM_KEYS, "PRE-02固定表单身份字段无效");
            String code = requireString(form, "formCode");
            int version = requirePositiveInteger(form, "formVersion");
            if (!codes.add(code) || !identities.add(new FormIdentity(code, version))) {
                throw new IllegalArgumentException("PRE-02固定表单身份重复");
            }
        }
        if (!codes.equals(BASELINE_ITEMS) || identities.size() != BASELINE_ITEMS.size()) {
            throw new IllegalArgumentException("PRE-02固定表单目录未精确覆盖六类基准项");
        }
        return new Catalog(catalogVersion, Set.copyOf(identities));
    }

    private static void validateCommonFields(List<?> fields) {
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("PRE-02固定表单字段缺失");
        }
        Set<String> codes = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (Object value : fields) {
            Map<?, ?> field = requireMap(value, "PRE-02固定表单字段无效");
            if (!FIELD_KEYS.containsAll(stringKeys(field)) || !stringKeys(field).containsAll(
                    Set.of("fieldCode", "fieldType", "required", "maxLength", "sortOrder"))) {
                throw new IllegalArgumentException("PRE-02固定表单字段不符合V1契约");
            }
            String code = requireString(field, "fieldCode");
            if (!codes.add(code) || !FIELD_TYPES.contains(requireString(field, "fieldType"))) {
                throw new IllegalArgumentException("PRE-02固定表单字段编码或类型无效");
            }
            requireBoolean(field, "required");
            requirePositiveInteger(field, "maxLength");
            int sortOrder = requireNonNegativeInteger(field, "sortOrder");
            if (!sortOrders.add(sortOrder)) {
                throw new IllegalArgumentException("PRE-02固定表单字段排序重复");
            }
            if (field.containsKey("options") && !(field.get("options") instanceof List<?>)) {
                throw new IllegalArgumentException("PRE-02固定表单选项无效");
            }
        }
    }

    private static void validateItems(List<?> items, Set<FormIdentity> forms) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("PRE-02工勘项配置缺失");
        }
        Set<String> itemCodes = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (Object value : items) {
            Map<?, ?> item = requireMap(value, "PRE-02工勘项配置无效");
            requireExactKeys(item, ITEM_KEYS, "PRE-02工勘项字段不符合V1契约");
            String itemCode = requireString(item, "itemCode");
            requireString(item, "itemName");
            requireBoolean(item, "enabled");
            String formCode = requireString(item, "formCode");
            int formVersion = requirePositiveInteger(item, "formVersion");
            requireBoolean(item, "evidenceRequired");
            if (!SOURCE_REQUIREMENTS.contains(requireString(item, "sourceRequirementCode"))) {
                throw new IllegalArgumentException("PRE-02工勘项来源策略无效");
            }
            requireBoolean(item, "waiverAllowed");
            if (!APPROVAL_ROLES.contains(requireString(item, "approvalRoleCode"))) {
                throw new IllegalArgumentException("PRE-02工勘项审批角色无效");
            }
            int sortOrder = requireNonNegativeInteger(item, "sortOrder");
            if (!itemCodes.add(itemCode) || !sortOrders.add(sortOrder)
                    || !forms.contains(new FormIdentity(formCode, formVersion))) {
                throw new IllegalArgumentException("PRE-02工勘项身份、排序或表单引用无效");
            }
        }
        if (!itemCodes.equals(BASELINE_ITEMS)) {
            throw new IllegalArgumentException("PRE-02模板未精确覆盖六类基准项");
        }
    }

    private static Map<?, ?> requireObject(String json, String message) {
        Map<?, ?> value = JsonUtils.parseObjectQuietly(json, Map.class);
        if (value == null) {
            throw new IllegalArgumentException(message);
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
                throw new IllegalArgumentException("PRE-02 JSON字段名无效");
            }
            keys.add(text);
        }
        return keys;
    }

    private static String requireString(Map<?, ?> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("PRE-02字段" + key + "无效");
        }
        return text;
    }

    private static void requireBoolean(Map<?, ?> value, String key) {
        if (!(value.get(key) instanceof Boolean)) {
            throw new IllegalArgumentException("PRE-02字段" + key + "无效");
        }
    }

    private static void requireInteger(Map<?, ?> value, String key, int expected) {
        if (requireIntegerValue(value, key) != expected) {
            throw new IllegalArgumentException("PRE-02字段" + key + "无效");
        }
    }

    private static int requirePositiveInteger(Map<?, ?> value, String key) {
        int number = requireIntegerValue(value, key);
        if (number <= 0) {
            throw new IllegalArgumentException("PRE-02字段" + key + "无效");
        }
        return number;
    }

    private static int requireNonNegativeInteger(Map<?, ?> value, String key) {
        int number = requireIntegerValue(value, key);
        if (number < 0) {
            throw new IllegalArgumentException("PRE-02字段" + key + "无效");
        }
        return number;
    }

    private static int requireIntegerValue(Map<?, ?> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("PRE-02字段" + key + "无效");
        }
        return number.intValue();
    }

    public record ParsedBinding(
            String preparationTemplateCode,
            Integer preparationTemplateRevision,
            Integer fixedFormCatalogVersion,
            String itemConfigurationSnapshot) {
    }

    private record FormIdentity(String code, int version) {
    }

    private record Catalog(int version, Set<FormIdentity> forms) {
    }
}
