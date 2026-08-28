package cn.iocoder.yudao.module.pms.project.domain.template;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** PRE-04需求分析WorkBinding的封闭V2动态表单修订快照。 */
public final class RequirementAnalysisWorkBindingSchema {

    public static final String BINDING_TYPE = "BUSINESS_OBJECT";
    public static final String TARGET_CONTEXT = "SOL";
    public static final String TARGET_OBJECT_TYPE = "REQUIREMENT_ANALYSIS";
    public static final String TARGET_OBJECT_KEY = "PRE_04_REQUIREMENT_ANALYSIS";
    public static final String PROVIDER_KEY = "SOL/REQUIREMENT_ANALYSIS";
    public static final int SCHEMA_VERSION = 2;

    private static final Set<String> ROOT_KEYS = Set.of(
            "schemaVersion", "dynamicFormTemplateId", "dynamicFormTemplateRevisionId",
            "dynamicFormRevisionNo", "dynamicFormRevisionFactVersion");

    private RequirementAnalysisWorkBindingSchema() {
    }

    public static boolean isRequirementAnalysisBinding(TemplateDefinitionContent.TaskDef task) {
        return task != null
                && BINDING_TYPE.equals(task.getWorkBindingTypeCode())
                && TARGET_CONTEXT.equals(task.getTargetContextCode())
                && TARGET_OBJECT_TYPE.equals(task.getTargetObjectType())
                && TARGET_OBJECT_KEY.equals(task.getTargetObjectKey());
    }

    /** 发布前解析管理员选择的明确PLT发布修订。 */
    public static ParsedBinding parseForPublication(String bindingJson) {
        return parse(bindingJson, "PRE-04绑定配置字段不符合V2契约");
    }

    /** 运行时解析项目执行契约中冻结的同一PLT修订事实。 */
    public static ParsedBinding parseFrozen(String bindingJson) {
        return parse(bindingJson, "PRE-04冻结配置字段不符合V2契约");
    }

    /** 仅从PLT权威修订事实构造稳定快照，不携带Schema正文或旧目录扩展项。 */
    public static String toSnapshot(ParsedBinding binding) {
        if (binding == null) {
            throw new IllegalArgumentException("PRE-04修订事实缺失");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SCHEMA_VERSION);
        snapshot.put("dynamicFormTemplateId", positive(binding.dynamicFormTemplateId(), "dynamicFormTemplateId"));
        snapshot.put("dynamicFormTemplateRevisionId",
                positive(binding.dynamicFormTemplateRevisionId(), "dynamicFormTemplateRevisionId"));
        snapshot.put("dynamicFormRevisionNo", positive(binding.dynamicFormRevisionNo(), "dynamicFormRevisionNo"));
        snapshot.put("dynamicFormRevisionFactVersion",
                positive(binding.dynamicFormRevisionFactVersion(), "dynamicFormRevisionFactVersion"));
        return JsonUtils.toJsonString(snapshot);
    }

    private static ParsedBinding parse(String bindingJson, String fieldMessage) {
        Map<?, ?> binding = JsonUtils.parseObjectQuietly(bindingJson, Map.class);
        if (binding == null) {
            throw new IllegalArgumentException("PRE-04绑定配置不是合法JSON对象");
        }
        requireExactKeys(binding, ROOT_KEYS, fieldMessage);
        if (integer(binding, "schemaVersion") != SCHEMA_VERSION) {
            throw new IllegalArgumentException("PRE-04字段schemaVersion无效");
        }
        return new ParsedBinding(
                longValue(binding, "dynamicFormTemplateId"),
                longValue(binding, "dynamicFormTemplateRevisionId"),
                integer(binding, "dynamicFormRevisionNo"),
                integer(binding, "dynamicFormRevisionFactVersion"));
    }

    private static void requireExactKeys(Map<?, ?> value, Set<String> expected, String message) {
        Set<String> keys = new HashSet<>();
        for (Object key : value.keySet()) {
            if (!(key instanceof String text)) {
                throw new IllegalArgumentException(message);
            }
            keys.add(text);
        }
        if (!keys.equals(expected)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static int integer(Map<?, ?> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("PRE-04字段" + key + "无效");
        }
        return positive(number.intValue(), key);
    }

    private static long longValue(Map<?, ?> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof Number number) || number.doubleValue() != number.longValue()) {
            throw new IllegalArgumentException("PRE-04字段" + key + "无效");
        }
        return positive(number.longValue(), key);
    }

    private static int positive(Integer value, String key) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("PRE-04字段" + key + "无效");
        }
        return value;
    }

    private static long positive(Long value, String key) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("PRE-04字段" + key + "无效");
        }
        return value;
    }

    public record ParsedBinding(
            Long dynamicFormTemplateId,
            Long dynamicFormTemplateRevisionId,
            Integer dynamicFormRevisionNo,
            Integer dynamicFormRevisionFactVersion) {
    }
}
