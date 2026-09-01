package cn.iocoder.yudao.module.pms.engineering.domain.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F-SOL-003固定核心章节与项目冻结扩展项解析器。
 *
 * @deprecated 已由PLT动态表单冻结修订与SOL业务策略替代；不得用于新实现。
 */
@Deprecated
public final class RequirementAnalysisCatalog {

    public static final String CATALOG_CODE = "PRE_04_REQUIREMENT_ANALYSIS";
    public static final int CATALOG_VERSION = 1;
    private static final Set<String> FIELD_TYPES = Set.of(
            "RICH_TEXT", "TEXT", "NUMBER", "BOOLEAN", "SINGLE_SELECT", "MULTI_SELECT");
    private static final Set<String> SELECT_TYPES = Set.of("SINGLE_SELECT", "MULTI_SELECT");
    private static final List<SectionDefinition> CORE = List.of(
            core("PROJECT_BACKGROUND", "项目背景", true, 10),
            core("PROJECT_OBJECTIVE", "项目目标", true, 20),
            core("NETWORK_TOPOLOGY", "网络拓扑", true, 30),
            core("TRANSMISSION_REQUIREMENT", "传输需求", false, 40),
            core("TRAFFIC_REQUIREMENT", "流量需求", false, 50),
            core("BUSINESS_REQUIREMENT", "业务需求", false, 60),
            core("IP_PLANNING", "IP规划", false, 70),
            core("REDUNDANCY_REQUIREMENT", "冗余需求", false, 80),
            core("SECURITY_PROTECTION", "安全防护", false, 90),
            core("OPERATIONS_REQUIREMENT", "运维需求", false, 100),
            core("LOGGING_REQUIREMENT", "日志需求", false, 110));

    private RequirementAnalysisCatalog() {
    }

    public static List<SectionDefinition> parse(String snapshot) {
        Map<?, ?> root = JsonUtils.parseObject(snapshot, Map.class);
        if (root == null || !Integer.valueOf(1).equals(number(root.get("schemaVersion")))
                || !CATALOG_CODE.equals(root.get("catalogCode"))
                || !Integer.valueOf(CATALOG_VERSION).equals(number(root.get("catalogVersion")))) {
            throw new IllegalArgumentException("invalid PRE-04 catalog snapshot");
        }
        List<SectionDefinition> result = new ArrayList<>(CORE);
        Set<String> codes = new HashSet<>();
        CORE.forEach(section -> codes.add(section.sectionCode()));
        Object extensions = root.get("extensionItems");
        if (!(extensions instanceof List<?> rows)) {
            throw new IllegalArgumentException("extensionItems must be an array");
        }
        for (Object value : rows) {
            if (!(value instanceof Map<?, ?> row)) throw new IllegalArgumentException("invalid extension item");
            String fieldCode = text(row.get("fieldCode"));
            String fieldName = text(row.get("fieldName"));
            String fieldType = text(row.get("fieldTypeCode"));
            Boolean required = bool(row.get("required"));
            Integer sortOrder = number(row.get("sortOrder"));
            String dictionaryType = nullableText(row.get("dictionaryType"));
            if (fieldCode.isBlank() || fieldCode.length() > 64 || fieldName.isBlank()
                    || !FIELD_TYPES.contains(fieldType)
                    || required == null || sortOrder == null || sortOrder < 0 || !codes.add(fieldCode)) {
                throw new IllegalArgumentException("invalid extension item definition");
            }
            List<Option> options = parseOptions(row.get("optionSnapshot"));
            if (SELECT_TYPES.contains(fieldType)) {
                if (dictionaryType == null || options.isEmpty()) {
                    throw new IllegalArgumentException("selection extension requires frozen options");
                }
            } else if (dictionaryType != null || !options.isEmpty()) {
                throw new IllegalArgumentException("non-selection extension cannot carry dictionary options");
            }
            result.add(new SectionDefinition(fieldCode, fieldName, "EXTENSION", fieldType,
                    required, dictionaryType, sortOrder, options));
        }
        result.sort(Comparator.comparing(SectionDefinition::sortOrder)
                .thenComparing(SectionDefinition::sectionCode));
        return List.copyOf(result);
    }

    public static String schemaSnapshot(SectionDefinition definition) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("sectionCode", definition.sectionCode());
        snapshot.put("sectionKindCode", definition.sectionKindCode());
        snapshot.put("fieldTypeCode", definition.fieldTypeCode());
        snapshot.put("required", definition.required());
        if (definition.dictionaryType() != null) snapshot.put("dictionaryType", definition.dictionaryType());
        snapshot.put("optionSnapshot", definition.options());
        return JsonUtils.toJsonString(snapshot);
    }

    private static List<Option> parseOptions(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> rows)) throw new IllegalArgumentException("invalid option snapshot");
        List<Option> options = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (Object item : rows) {
            if (!(item instanceof Map<?, ?> row)) throw new IllegalArgumentException("invalid option");
            String code = text(row.get("code"));
            String label = text(row.get("label"));
            if (code.isBlank() || label.isBlank() || !codes.add(code)) {
                throw new IllegalArgumentException("invalid frozen option");
            }
            options.add(new Option(code, label));
        }
        options.sort(Comparator.comparing(Option::code));
        return List.copyOf(options);
    }

    private static SectionDefinition core(String code, String name, boolean required, int order) {
        return new SectionDefinition(code, name, "CORE", "RICH_TEXT", required, null, order, List.of());
    }

    private static String text(Object value) {
        if (!(value instanceof String text)) throw new IllegalArgumentException("text field required");
        return text.trim();
    }

    private static String nullableText(Object value) {
        if (value == null) return null;
        String text = text(value);
        return text.isBlank() ? null : text;
    }

    private static Integer number(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Boolean bool(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    public record SectionDefinition(String sectionCode, String sectionName, String sectionKindCode,
                                    String fieldTypeCode, boolean required, String dictionaryType,
                                    Integer sortOrder, List<Option> options) {
        public SectionDefinition {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record Option(String code, String label) {
    }
}
