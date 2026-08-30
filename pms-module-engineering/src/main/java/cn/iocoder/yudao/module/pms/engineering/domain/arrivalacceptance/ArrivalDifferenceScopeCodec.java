package cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** ArrivalDifference.scope_snapshot的严格判别联合编解码。 */
public final class ArrivalDifferenceScopeCodec {

    public static final String DEVICE = "DEVICE";
    public static final String ORDER_MODEL_QUANTITY = "ORDER_MODEL_QUANTITY";
    private static final Set<String> DEVICE_KEYS = Set.of("scopeType", "deviceId");
    private static final Set<String> QUANTITY_KEYS = Set.of(
            "scopeType", "orderLineId", "productCode", "modelCode", "quantity", "unitCode");

    private ArrivalDifferenceScopeCodec() {
    }

    public static Scope parse(String snapshot) {
        try {
            JsonNode root = JsonUtils.parseTree(snapshot);
            if (root == null || !root.isObject()) throw invalid();
            JsonNode type = root.get("scopeType");
            if (type == null || !type.isString()) throw invalid();
            return switch (type.asText()) {
                case DEVICE -> parseDevice(root);
                case ORDER_MODEL_QUANTITY -> parseQuantity(root);
                default -> throw invalid();
            };
        } catch (RuntimeException ignored) {
            throw invalid();
        }
    }

    public static String serialize(Scope scope) {
        if (scope instanceof DeviceScope device) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("scopeType", DEVICE);
            value.put("deviceId", device.deviceId());
            return JsonUtils.toJsonString(value);
        }
        if (scope instanceof QuantityScope quantity) {
            var value = JsonUtils.getObjectMapper().createObjectNode();
            value.put("scopeType", ORDER_MODEL_QUANTITY);
            value.put("orderLineId", quantity.orderLineId());
            if (quantity.productCode() == null) value.putNull("productCode");
            else value.put("productCode", quantity.productCode());
            if (quantity.modelCode() == null) value.putNull("modelCode");
            else value.put("modelCode", quantity.modelCode());
            value.put("quantity", quantity.quantity());
            value.put("unitCode", quantity.unitCode());
            return JsonUtils.toJsonString(value);
        }
        throw invalid();
    }

    private static DeviceScope parseDevice(JsonNode root) {
        requireExactKeys(root, DEVICE_KEYS);
        return new DeviceScope(positiveLong(root.get("deviceId")));
    }

    private static QuantityScope parseQuantity(JsonNode root) {
        requireExactKeys(root, QUANTITY_KEYS);
        JsonNode quantityNode = root.get("quantity");
        if (quantityNode == null || !quantityNode.isNumber()) throw invalid();
        BigDecimal quantity = quantityNode.decimalValue();
        if (quantity.signum() <= 0) throw invalid();
        return new QuantityScope(positiveLong(root.get("orderLineId")),
                nullableNormalizedText(root.get("productCode")),
                nullableNormalizedText(root.get("modelCode")),
                quantity, normalizedText(root.get("unitCode")));
    }

    private static void requireExactKeys(JsonNode root, Set<String> expected) {
        Set<String> actual = new HashSet<>();
        root.properties().forEach(entry -> actual.add(entry.getKey()));
        if (!expected.equals(actual)) throw invalid();
    }

    private static Long positiveLong(JsonNode node) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) throw invalid();
        long value = node.longValue();
        if (value <= 0) throw invalid();
        return value;
    }

    private static String nullableNormalizedText(JsonNode node) {
        if (node == null) throw invalid();
        return node.isNull() ? null : normalizedText(node);
    }

    private static String normalizedText(JsonNode node) {
        if (node == null || !node.isString()) throw invalid();
        String value = node.asText();
        if (value.isBlank() || !value.equals(value.trim())) throw invalid();
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("invalid arrival difference scope snapshot");
    }

    public sealed interface Scope permits DeviceScope, QuantityScope {
    }

    public record DeviceScope(Long deviceId) implements Scope {
        public DeviceScope {
            if (deviceId == null || deviceId <= 0) throw invalid();
        }
    }

    public record QuantityScope(Long orderLineId, String productCode, String modelCode,
                                BigDecimal quantity, String unitCode) implements Scope {
        public QuantityScope {
            productCode = normalizeNullable(productCode);
            modelCode = normalizeNullable(modelCode);
            unitCode = normalizeRequired(unitCode);
            if (orderLineId == null || orderLineId <= 0 || productCode == null && modelCode == null
                    || quantity == null || quantity.signum() <= 0) throw invalid();
        }

        private static String normalizeNullable(String value) {
            if (value == null) return null;
            String normalized = value.trim();
            return normalized.isEmpty() ? null : normalized;
        }

        private static String normalizeRequired(String value) {
            if (value == null || value.trim().isEmpty()) throw invalid();
            return value.trim();
        }
    }
}
