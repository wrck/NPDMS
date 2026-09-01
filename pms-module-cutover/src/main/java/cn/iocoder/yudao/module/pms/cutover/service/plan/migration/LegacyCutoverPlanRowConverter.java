package cn.iocoder.yudao.module.pms.cutover.service.plan.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordFact;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将PLT冻结的pms_cut_plan来源行转换为CUT不可变方案投影。 */
@Component
public class LegacyCutoverPlanRowConverter {

    public static final String MAPPING_VERSION = "FCUT004_LEGACY_V1";
    private static final Set<String> SOURCE_KEYS = Set.of("id", "task_id", "code", "name", "pre_check",
            "procedure", "verification", "rollback", "level", "status", "approved_by", "approved_time",
            "approval_opinion", "baseline_version", "remark", "version", "creator", "create_time", "updater",
            "update_time", "deleted", "tenant_id");

    public ConvertedLegacyPlan convert(Long trustedTenantId, MigrationSourceRecordFact source) {
        requirePositive(trustedTenantId, "tenantId");
        if (source == null || !trustedTenantId.equals(source.tenantId())) {
            throw invalid("PLT来源租户不匹配");
        }
        JsonNode root;
        try {
            root = JsonUtils.parseObject(source.sourcePayloadJson(), JsonNode.class);
        } catch (RuntimeException exception) {
            throw invalid("旧方案来源JSON非法");
        }
        if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(SOURCE_KEYS)) {
            throw invalid("旧方案来源字段集合非法");
        }
        Long sourceId = positiveLong(root, "id");
        if (!sourceId.toString().equals(source.sourcePk())) {
            throw invalid("旧方案来源主键不匹配");
        }
        Long sourceTenantId = positiveLong(root, "tenant_id");
        if (!trustedTenantId.equals(sourceTenantId)) {
            throw invalid("旧方案来源tenant_id不匹配");
        }
        Long sourceTaskId = positiveLong(root, "task_id");
        boolean deleted = booleanValue(root, "deleted");
        String code = normalizedText(root, "code", 64, false);
        String name = normalizedText(root, "name", 128, false);
        String level = normalizedText(root, "level", 1, false).toUpperCase(Locale.ROOT);
        if (!Set.of("A", "B", "C", "D").contains(level)) {
            throw invalid("旧方案level非法");
        }
        int status = boundedInt(root, "status", 0, 4);
        int version = boundedInt(root, "version", 0, Integer.MAX_VALUE);
        String creator = auditText(root, "creator");
        String updater = auditText(root, "updater");
        LocalDateTime createTime = time(root, "create_time");
        LocalDateTime updateTime = time(root, "update_time");
        String remark = nullableNormalizedText(root, "remark", 4000);

        List<LegacyStep> steps = new ArrayList<>();
        if (!deleted) {
            addStep(steps, root, "pre_check", "PRE_OPERATION");
            addStep(steps, root, "procedure", "OPERATION");
            addStep(steps, root, "verification", "POST_BUSINESS_TEST");
            addStep(steps, root, "rollback", "ROLLBACK");
        }
        if (!deleted && steps.isEmpty()) {
            throw invalid("旧方案没有可迁移步骤");
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourceTable", "pms_cut_plan");
        snapshot.put("sourceId", sourceId);
        snapshot.put("sourceTenantId", sourceTenantId);
        snapshot.put("sourceTaskId", sourceTaskId);
        snapshot.put("sourceVersion", version);
        snapshot.put("sourceStatusRaw", status);
        snapshot.put("mappingVersion", MAPPING_VERSION);
        snapshot.put("code", code);
        snapshot.put("name", name);
        snapshot.put("level", level);
        snapshot.put("remark", remark);
        return new ConvertedLegacyPlan(sourceId, sourceTaskId, status, version, deleted,
                JsonUtils.toJsonString(snapshot), creator, createTime, updater, updateTime, List.copyOf(steps));
    }

    private static void addStep(List<LegacyStep> steps, JsonNode root, String field, String section) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return;
        }
        if (!node.isTextual()) {
            throw invalid(field + "必须为文本或null");
        }
        String value = node.asText().trim();
        if (value.isEmpty()) {
            return;
        }
        if (value.length() > 4000) {
            throw invalid(field + "长度非法");
        }
        steps.add(new LegacyStep(section, value));
    }

    private static Long positiveLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) {
            throw invalid(field + "必须为正整数");
        }
        return node.asLong();
    }

    private static int boundedInt(JsonNode root, String field, int min, int max) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid(field + "必须为整数");
        }
        int value = node.asInt();
        if (value < min || value > max) {
            throw invalid(field + "超出范围");
        }
        return value;
    }

    private static boolean booleanValue(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isBoolean()) {
            throw invalid(field + "必须为boolean");
        }
        return node.asBoolean();
    }

    private static String normalizedText(JsonNode root, String field, int max, boolean allowEmpty) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual()) {
            throw invalid(field + "必须为文本");
        }
        String value = node.asText().trim();
        if ((!allowEmpty && value.isEmpty()) || value.length() > max) {
            throw invalid(field + "长度非法");
        }
        return value;
    }

    private static String nullableNormalizedText(JsonNode root, String field, int max) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return normalizedText(root, field, max, true);
    }

    private static String auditText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().length() > 64) {
            throw invalid(field + "审计字段非法");
        }
        return node.asText();
    }

    private static LocalDateTime time(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            throw invalid(field + "不能为空");
        }
        try {
            LocalDateTime value = JsonUtils.parseObject(node.toString(), LocalDateTime.class);
            if (value == null) {
                throw invalid(field + "时间非法");
            }
            return value;
        } catch (RuntimeException exception) {
            throw invalid(field + "时间非法");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw invalid(field + "必须为正整数");
        }
    }

    private static LegacyCutoverPlanMigrationException invalid(String message) {
        return new LegacyCutoverPlanMigrationException(message);
    }

    public record ConvertedLegacyPlan(Long legacyPlanId, Long legacyTaskId, Integer legacyStatus,
                                      Integer legacyVersion, boolean deleted, String sourceSnapshot,
                                      String creator, LocalDateTime createTime, String updater,
                                      LocalDateTime updateTime, List<LegacyStep> steps) {
    }

    public record LegacyStep(String sectionCode, String content) {
    }
}
