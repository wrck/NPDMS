package cn.iocoder.yudao.module.pms.cutover.service.closure.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.MigrationSourceRecordFact;
import tools.jackson.databind.JsonNode;

import java.util.Set;

/** Validates a frozen legacy execution row without turning it into a P6 closure. */
public class LegacyCutoverClosureRowClassifier {

    public static final String RULE_VERSION = "FCUT006_LEGACY_V1";
    private static final Set<String> SOURCE_KEYS = Set.of("id", "task_id", "code", "step_name",
            "operator_user_id", "operation_time", "result", "exception_record", "evidence_url", "status",
            "remark", "version", "creator", "create_time", "updater", "update_time", "deleted", "tenant_id");

    public Disposition classify(Long trustedTenantId, MigrationSourceRecordFact source) {
        if (trustedTenantId == null || trustedTenantId <= 0 || source == null
                || !trustedTenantId.equals(source.tenantId())) {
            return Disposition.INVALID_SOURCE;
        }
        try {
            JsonNode root = JsonUtils.parseObject(source.sourcePayloadJson(), JsonNode.class);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(SOURCE_KEYS)) {
                return Disposition.INVALID_SOURCE;
            }
            long sourceId = positiveLong(root, "id");
            if (!Long.toString(sourceId).equals(source.sourcePk())
                    || positiveLong(root, "tenant_id") != trustedTenantId
                    || positiveLong(root, "task_id") <= 0) {
                return Disposition.INVALID_SOURCE;
            }
            requiredText(root, "code", 64);
            requiredText(root, "step_name", 255);
            nullablePositiveLong(root, "operator_user_id");
            nullableText(root, "operation_time", Integer.MAX_VALUE);
            nullableText(root, "result", Integer.MAX_VALUE);
            nullableText(root, "exception_record", Integer.MAX_VALUE);
            nullableText(root, "evidence_url", 500);
            boundedInt(root, "status", 0, 4);
            nullableText(root, "remark", 500);
            boundedInt(root, "version", 0, Integer.MAX_VALUE);
            requiredTextAllowEmpty(root, "creator", 64);
            requiredText(root, "create_time", 64);
            requiredTextAllowEmpty(root, "updater", 64);
            requiredText(root, "update_time", 64);
            JsonNode deleted = root.get("deleted");
            if (deleted == null || !deleted.isBoolean()) return Disposition.INVALID_SOURCE;
            return Disposition.RETAINED;
        } catch (RuntimeException ignored) {
            return Disposition.INVALID_SOURCE;
        }
    }

    private static long positiveLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) {
            throw new IllegalArgumentException(field);
        }
        return node.asLong();
    }

    private static void nullablePositiveLong(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node != null && !node.isNull() && (!node.isIntegralNumber() || !node.canConvertToLong()
                || node.asLong() <= 0)) throw new IllegalArgumentException(field);
    }

    private static void boundedInt(JsonNode root, String field, int min, int max) {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()
                || node.asInt() < min || node.asInt() > max) throw new IllegalArgumentException(field);
    }

    private static void requiredText(JsonNode root, String field, int max) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank() || node.asText().length() > max) {
            throw new IllegalArgumentException(field);
        }
    }

    private static void requiredTextAllowEmpty(JsonNode root, String field, int max) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().length() > max) {
            throw new IllegalArgumentException(field);
        }
    }

    private static void nullableText(JsonNode root, String field, int max) {
        JsonNode node = root.get(field);
        if (node != null && !node.isNull() && (!node.isTextual() || node.asText().length() > max)) {
            throw new IllegalArgumentException(field);
        }
    }

    public enum Disposition {
        RETAINED,
        INVALID_SOURCE
    }
}
