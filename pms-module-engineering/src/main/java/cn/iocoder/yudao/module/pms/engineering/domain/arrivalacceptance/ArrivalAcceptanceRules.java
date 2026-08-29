package cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance;

import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalQuantityScopeFact;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 草稿提交与确认前可由纯领域数据判定的到货范围规则。 */
public final class ArrivalAcceptanceRules {

    public void validateSubmission(Set<Long> expectedDeviceIds,
                                   List<ArrivalQuantityScopeFact> expectedQuantityScopes,
                                   Set<Long> acceptedDeviceIds,
                                   List<ArrivalQuantityScopeFact> acceptedQuantityScopes,
                                   boolean hasEvidenceRevision) {
        if (expectedDeviceIds == null || expectedQuantityScopes == null
                || acceptedDeviceIds == null || acceptedQuantityScopes == null) {
            throw new IllegalArgumentException("arrival scope is required");
        }
        if (expectedDeviceIds.isEmpty() && expectedQuantityScopes.isEmpty()) {
            throw new IllegalStateException("expected arrival scope is empty");
        }
        if (!hasEvidenceRevision) {
            throw new IllegalStateException("arrival evidence revision is required");
        }
        if (!expectedDeviceIds.containsAll(acceptedDeviceIds)) {
            throw new IllegalStateException("accepted device is outside expected scope");
        }
        Map<QuantityKey, BigDecimal> expected = quantities(expectedQuantityScopes, true);
        Map<QuantityKey, BigDecimal> accepted = quantities(acceptedQuantityScopes, false);
        for (Map.Entry<QuantityKey, BigDecimal> entry : accepted.entrySet()) {
            BigDecimal expectedQuantity = expected.get(entry.getKey());
            if (expectedQuantity == null || entry.getValue().compareTo(expectedQuantity) > 0) {
                throw new IllegalStateException("accepted quantity exceeds expected scope");
            }
        }
    }

    private static Map<QuantityKey, BigDecimal> quantities(
            List<ArrivalQuantityScopeFact> scopes, boolean rejectDuplicate) {
        Map<QuantityKey, BigDecimal> quantities = new HashMap<>();
        for (ArrivalQuantityScopeFact scope : scopes) {
            if (scope == null) {
                throw new IllegalArgumentException("arrival quantity scope contains null");
            }
            QuantityKey key = QuantityKey.from(scope);
            BigDecimal previous = quantities.putIfAbsent(key, scope.quantity());
            if (previous != null) {
                if (rejectDuplicate) {
                    throw new IllegalStateException("expected arrival quantity scope is duplicated");
                }
                quantities.put(key, previous.add(scope.quantity()));
            }
        }
        return quantities;
    }

    static record QuantityKey(Long orderLineId, String productCode, String modelCode, String unitCode)
            implements Comparable<QuantityKey> {

        static QuantityKey from(ArrivalQuantityScopeFact scope) {
            return new QuantityKey(scope.orderLineId(), scope.productCode(), scope.modelCode(), scope.unitCode());
        }

        ArrivalQuantityScopeFact quantity(BigDecimal value) {
            return new ArrivalQuantityScopeFact(orderLineId, productCode, modelCode, value, unitCode);
        }

        @Override
        public int compareTo(QuantityKey other) {
            int compared = orderLineId.compareTo(other.orderLineId);
            if (compared != 0) return compared;
            compared = compareNullable(productCode, other.productCode);
            if (compared != 0) return compared;
            compared = compareNullable(modelCode, other.modelCode);
            if (compared != 0) return compared;
            return unitCode.compareTo(other.unitCode);
        }

        private static int compareNullable(String left, String right) {
            if (left == null) return right == null ? 0 : -1;
            return right == null ? 1 : left.compareTo(right);
        }
    }
}
