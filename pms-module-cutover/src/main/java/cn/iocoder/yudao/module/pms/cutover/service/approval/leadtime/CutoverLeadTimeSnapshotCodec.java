package cn.iocoder.yudao.module.pms.cutover.service.approval.leadtime;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Strict JSON codec for the immutable F-CUT-008 lead-time snapshot.
 */
public final class CutoverLeadTimeSnapshotCodec {

    private static final long MAX_SAFE_WIRE_LONG = 9_007_199_254_740_991L;
    private static final Set<String> EXACT_KEYS = Set.of("ruleVersion", "timezoneId", "cutoverType",
            "scheduledTime", "planSubmittedAt", "requiredDays", "actualNaturalDays", "lateSubmission");

    public String encode(CutoverLeadTimeCompliance value) {
        require(value != null, "leadTimeCompliance");
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("ruleVersion", value.ruleVersion());
        root.put("timezoneId", value.timezoneId());
        root.put("cutoverType", value.cutoverType());
        putWireLong(root, "scheduledTime", value.scheduledTime());
        putWireLong(root, "planSubmittedAt", value.planSubmittedAt());
        root.put("requiredDays", value.requiredDays());
        root.put("actualNaturalDays", value.actualNaturalDays());
        root.put("lateSubmission", value.lateSubmission());
        decode(root);
        return JsonUtils.toJsonString(root);
    }

    public CutoverLeadTimeCompliance decode(String json) {
        require(json != null && !json.isBlank(), "leadTimeCompliance");
        return decode(JsonUtils.parseTree(json));
    }

    public CutoverLeadTimeCompliance decode(JsonNode root) {
        exact(root);
        return new CutoverLeadTimeCompliance(
                text(root, "ruleVersion"),
                text(root, "timezoneId"),
                text(root, "cutoverType"),
                positiveWireLong(root.get("scheduledTime"), "scheduledTime"),
                positiveWireLong(root.get("planSubmittedAt"), "planSubmittedAt"),
                positiveInt(root.get("requiredDays"), "requiredDays"),
                integer(root.get("actualNaturalDays"), "actualNaturalDays"),
                bool(root.get("lateSubmission"), "lateSubmission"));
    }

    private static void exact(JsonNode node) {
        require(node != null && node.isObject(), "leadTimeCompliance");
        Set<String> actual = new HashSet<>();
        node.properties().forEach(entry -> actual.add(entry.getKey()));
        require(actual.equals(EXACT_KEYS), "leadTimeCompliance keys");
    }

    private static String text(JsonNode node, String field) {
        require(node.path(field).isTextual(), field);
        String value = node.path(field).asText();
        require(!value.isBlank() && value.equals(value.trim()), field);
        return value;
    }

    private static int positiveInt(JsonNode node, String field) {
        require(node != null && node.isInt() && node.asInt() > 0, field);
        return node.asInt();
    }

    private static int integer(JsonNode node, String field) {
        require(node != null && node.isInt(), field);
        return node.asInt();
    }

    private static boolean bool(JsonNode node, String field) {
        require(node != null && node.isBoolean(), field);
        return node.asBoolean();
    }

    private static long positiveWireLong(JsonNode node, String field) {
        long value = wireLong(node, field);
        require(value > 0, field);
        return value;
    }

    private static long wireLong(JsonNode node, String field) {
        if (node != null && node.isIntegralNumber()) {
            require(node.canConvertToLong(), field);
            long value = node.asLong();
            require(value > -MAX_SAFE_WIRE_LONG && value < MAX_SAFE_WIRE_LONG, field);
            return value;
        }
        require(node != null && node.isTextual() && node.asText().matches("-?(0|[1-9][0-9]*)"), field);
        try {
            return Long.parseLong(node.asText());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid " + field, ex);
        }
    }

    private static void putWireLong(ObjectNode node, String field, long value) {
        if (value > -MAX_SAFE_WIRE_LONG && value < MAX_SAFE_WIRE_LONG) {
            node.put(field, value);
        } else {
            node.put(field, Long.toString(value));
        }
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new IllegalArgumentException("invalid " + field);
        }
    }
}
