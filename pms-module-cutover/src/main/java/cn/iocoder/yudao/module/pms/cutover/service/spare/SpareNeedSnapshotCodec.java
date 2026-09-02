package cn.iocoder.yudao.module.pms.cutover.service.spare;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot.AssessmentNeedSource;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot.ChecklistRiskNeedSource;
import cn.iocoder.yudao.module.pms.cutover.service.spare.model.SpareNeedSnapshot.NeedSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** SpareNeedSnapshot的严格JSON编解码。 */
public final class SpareNeedSnapshotCodec {
    private static final Set<String> ROOT_KEYS = Set.of("required", "sources");
    private static final Set<String> ASSESSMENT_KEYS = Set.of(
            "sourceType", "sourceId", "sourceVersion", "sparePartApplied");
    private static final Set<String> CHECKLIST_KEYS = Set.of(
            "sourceType", "sourceId", "sourceVersion", "stableItemKey", "applicable");
    private static final long MAX_SAFE_WIRE_LONG = 9_007_199_254_740_991L;

    public String encode(SpareNeedSnapshot snapshot) {
        if (snapshot == null) throw invalid("snapshot");
        ObjectNode root = JsonUtils.getObjectMapper().createObjectNode();
        root.put("required", snapshot.required());
        ArrayNode sources = root.putArray("sources");
        for (NeedSource source : snapshot.sources()) {
            ObjectNode node = sources.addObject();
            node.put("sourceType", source.sourceType());
            putWireLong(node, "sourceId", source.sourceId());
            node.put("sourceVersion", source.sourceVersion());
            if (source instanceof AssessmentNeedSource value) {
                node.put("sparePartApplied", value.sparePartApplied());
            } else if (source instanceof ChecklistRiskNeedSource value) {
                node.put("stableItemKey", value.stableItemKey());
                node.put("applicable", value.applicable());
            }
        }
        SpareNeedSnapshot decoded = decode(root);
        if (!decoded.equals(snapshot)) throw invalid("snapshot order");
        return JsonUtils.toJsonString(root);
    }

    public SpareNeedSnapshot decode(String json) {
        if (json == null || json.isBlank()) throw invalid("snapshot");
        return decode(JsonUtils.parseTree(json));
    }

    public SpareNeedSnapshot decode(JsonNode root) {
        exact(root, ROOT_KEYS, "snapshot");
        if (!root.path("required").isBoolean() || !root.path("sources").isArray()) throw invalid("snapshot");
        List<NeedSource> sources = new ArrayList<>();
        for (JsonNode node : root.path("sources")) {
            if (!node.path("sourceType").isTextual()) throw invalid("sourceType");
            switch (node.path("sourceType").asText()) {
                case "ASSESSMENT" -> {
                    exact(node, ASSESSMENT_KEYS, "assessment source");
                    if (!node.path("sparePartApplied").isBoolean()) throw invalid("sparePartApplied");
                    sources.add(new AssessmentNeedSource(positiveWireLong(node.path("sourceId"), "sourceId"),
                            positiveInt(node.path("sourceVersion"), "sourceVersion"),
                            node.path("sparePartApplied").asBoolean()));
                }
                case "CHECKLIST_RISK" -> {
                    exact(node, CHECKLIST_KEYS, "checklist source");
                    if (!node.path("stableItemKey").isTextual() || !node.path("applicable").isBoolean()) {
                        throw invalid("checklist source");
                    }
                    sources.add(new ChecklistRiskNeedSource(positiveWireLong(node.path("sourceId"), "sourceId"),
                            nonNegativeInt(node.path("sourceVersion"), "sourceVersion"),
                            node.path("stableItemKey").asText(), node.path("applicable").asBoolean()));
                }
                default -> throw invalid("sourceType");
            }
        }
        return new SpareNeedSnapshot(root.path("required").asBoolean(), sources);
    }

    private static void exact(JsonNode node, Set<String> expected, String field) {
        if (node == null || !node.isObject()) throw invalid(field);
        Set<String> actual = new HashSet<>();
        node.properties().forEach(entry -> actual.add(entry.getKey()));
        if (!actual.equals(expected)) throw invalid(field + " keys");
    }

    private static int positiveInt(JsonNode node, String field) {
        int value = nonNegativeInt(node, field);
        if (value == 0) throw invalid(field);
        return value;
    }

    private static int nonNegativeInt(JsonNode node, String field) {
        if (node == null || !node.isInt() || node.asInt() < 0) throw invalid(field);
        return node.asInt();
    }

    private static long positiveWireLong(JsonNode node, String field) {
        long value;
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            value = node.asLong();
            if (!(value > -MAX_SAFE_WIRE_LONG && value < MAX_SAFE_WIRE_LONG)) throw invalid(field);
        } else if (node.isTextual() && node.asText().matches("[1-9][0-9]*")) {
            try { value = Long.parseLong(node.asText()); }
            catch (NumberFormatException exception) { throw invalid(field); }
        } else throw invalid(field);
        if (value <= 0) throw invalid(field);
        return value;
    }

    private static void putWireLong(ObjectNode node, String field, long value) {
        if (value > -MAX_SAFE_WIRE_LONG && value < MAX_SAFE_WIRE_LONG) node.put(field, value);
        else node.put(field, Long.toString(value));
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("invalid " + field);
    }
}
