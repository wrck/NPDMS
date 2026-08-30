package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance.vo.ArrivalAcceptanceReqVO;
import tools.jackson.databind.JsonNode;

import java.util.Set;
import java.util.stream.Collectors;

/** 在记录反序列化前执行REST机器合同的精确键校验。 */
final class ArrivalAcceptanceRequestCodec {

    private static final Set<String> FILE = Set.of("artifactId", "referenceKey", "versionNo",
            "scopeVersion", "fileFactVersion", "hash");
    private static final Set<String> FILE_FACT = Set.of("artifactVersion", "referenceVersion", "availabilityVersion");

    private ArrivalAcceptanceRequestCodec() {
    }

    static ArrivalAcceptanceReqVO.Create create(JsonNode node) {
        exact(node, Set.of("projectId", "batchCode", "logisticsNo", "arrivedAt", "signerName",
                "expectedDeliveryScopeVersion"));
        return parse(node, ArrivalAcceptanceReqVO.Create.class);
    }

    static ArrivalAcceptanceReqVO.Patch patch(JsonNode node) {
        allowed(node, Set.of("logisticsNo", "arrivedAt", "signerName", "lines", "evidenceRevision"));
        if (node.isEmpty()) throw new IllegalArgumentException("empty patch request");
        if (node.has("lines") && !node.get("lines").isNull()) lines(node.get("lines"));
        if (node.has("evidenceRevision") && !node.get("evidenceRevision").isNull()) file(node.get("evidenceRevision"));
        return parse(node, ArrivalAcceptanceReqVO.Patch.class);
    }

    static ArrivalAcceptanceReqVO.RaiseDifference raise(JsonNode node) {
        exact(node, Set.of("arrivalLineId", "expectedLineVersion", "differenceTypeCode", "scopeSnapshot",
                "reason", "riskDescription", "evidenceRevision"));
        scope(node.get("scopeSnapshot"));
        file(node.get("evidenceRevision"));
        return parse(node, ArrivalAcceptanceReqVO.RaiseDifference.class);
    }

    static ArrivalAcceptanceReqVO.Resolution resolution(JsonNode node) {
        object(node);
        String type = text(node, "resolutionType");
        switch (type) {
            case "SUPPLEMENT" -> {
                exact(node, Set.of("resolutionType", "differenceId", "expectedDifferenceRevision",
                        "expectedDifferenceVersion", "supplementScope", "reason", "evidenceRevision"));
                scope(node.get("supplementScope")); file(node.get("evidenceRevision"));
            }
            case "KEEP_REJECTED", "CLOSE" -> {
                exact(node, Set.of("resolutionType", "differenceId", "expectedDifferenceRevision",
                        "expectedDifferenceVersion", "reason", "evidenceRevision"));
                file(node.get("evidenceRevision"));
            }
            case "EXEMPT" -> {
                exact(node, Set.of("resolutionType", "differenceId", "expectedDifferenceRevision",
                        "expectedDifferenceVersion", "reason", "riskDescription", "expiresAt", "evidenceRevision"));
                file(node.get("evidenceRevision"));
            }
            case "CORRECT_INFORMATION" -> {
                exact(node, Set.of("resolutionType", "expectedSourceVersion", "reason",
                        "correctionPatch", "evidenceRevision"));
                JsonNode correction = node.get("correctionPatch");
                exact(correction, Set.of("logisticsNo", "arrivedAt", "signerName", "lines"));
                if (!correction.get("lines").isNull()) lines(correction.get("lines"));
                file(node.get("evidenceRevision"));
            }
            default -> throw new IllegalArgumentException("unknown resolutionType");
        }
        return parse(node, ArrivalAcceptanceReqVO.Resolution.class);
    }

    static void empty(JsonNode node) {
        if (node != null && !node.isNull()) exact(node, Set.of());
    }

    private static void lines(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) throw new IllegalArgumentException("invalid lines");
        node.forEach(ArrivalAcceptanceRequestCodec::draftLine);
    }

    private static void draftLine(JsonNode node) {
        String type = text(node, "scopeType");
        if ("DEVICE".equals(type)) {
            exact(node, Set.of("scopeType", "lineId", "expectedLineVersion", "deviceId", "received"));
        } else if ("ORDER_MODEL_QUANTITY".equals(type)) {
            exact(node, Set.of("scopeType", "lineId", "expectedLineVersion", "orderLineId", "productCode",
                    "modelCode", "acceptedQuantity", "unitCode"));
        } else {
            throw new IllegalArgumentException("unknown line scopeType");
        }
    }

    private static void scope(JsonNode node) {
        String type = text(node, "scopeType");
        if ("DEVICE".equals(type)) {
            exact(node, Set.of("scopeType", "deviceId"));
        } else if ("ORDER_MODEL_QUANTITY".equals(type)) {
            exact(node, Set.of("scopeType", "orderLineId", "productCode", "modelCode", "quantity", "unitCode"));
        } else {
            throw new IllegalArgumentException("unknown scopeType");
        }
    }

    private static void file(JsonNode node) {
        exact(node, FILE);
        exact(node.get("fileFactVersion"), FILE_FACT);
    }

    private static String text(JsonNode node, String field) {
        object(node);
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value.asText();
    }

    private static void exact(JsonNode node, Set<String> keys) {
        object(node);
        Set<String> actual = node.propertyStream().map(java.util.Map.Entry::getKey).collect(Collectors.toSet());
        if (!actual.equals(keys)) throw new IllegalArgumentException("request keys do not match contract");
    }

    private static void allowed(JsonNode node, Set<String> keys) {
        object(node);
        Set<String> actual = node.propertyStream().map(java.util.Map.Entry::getKey).collect(Collectors.toSet());
        if (!keys.containsAll(actual)) throw new IllegalArgumentException("request contains forbidden keys");
    }

    private static void object(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("request body must be an object");
    }

    private static <T> T parse(JsonNode node, Class<T> type) {
        return JsonUtils.parseObject(node.toString(), type);
    }
}
