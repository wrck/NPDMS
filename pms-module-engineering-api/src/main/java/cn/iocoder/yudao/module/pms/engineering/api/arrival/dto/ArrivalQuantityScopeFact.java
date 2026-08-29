package cn.iocoder.yudao.module.pms.engineering.api.arrival.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** 无明确SN时按订单行、产品/型号、单位和数量表达的范围事实。 */
public record ArrivalQuantityScopeFact(
        Long orderLineId,
        String productCode,
        String modelCode,
        BigDecimal quantity,
        String unitCode) implements Comparable<ArrivalQuantityScopeFact> {

    public ArrivalQuantityScopeFact {
        productCode = trimToNull(productCode);
        modelCode = trimToNull(modelCode);
        unitCode = trimToNull(unitCode);
        if (orderLineId == null || orderLineId <= 0
                || productCode == null && modelCode == null
                || quantity == null || quantity.signum() <= 0
                || unitCode == null) {
            throw new IllegalArgumentException("invalid arrival quantity scope fact");
        }
    }

    @Override
    public int compareTo(ArrivalQuantityScopeFact other) {
        int compared = orderLineId.compareTo(other.orderLineId);
        if (compared != 0) return compared;
        compared = compareNullable(productCode, other.productCode);
        if (compared != 0) return compared;
        compared = compareNullable(modelCode, other.modelCode);
        if (compared != 0) return compared;
        compared = unitCode.compareTo(other.unitCode);
        if (compared != 0) return compared;
        return quantity.compareTo(other.quantity);
    }

    static List<ArrivalQuantityScopeFact> normalize(List<ArrivalQuantityScopeFact> scopes) {
        if (scopes == null) {
            throw new IllegalArgumentException("arrival quantity scopes are required");
        }
        TreeSet<ArrivalQuantityScopeFact> ordered = new TreeSet<>();
        for (ArrivalQuantityScopeFact scope : scopes) {
            if (scope == null || !ordered.add(scope)) {
                throw new IllegalArgumentException("arrival quantity scopes contain duplicate or null item");
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(ordered));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int compareNullable(String left, String right) {
        if (left == null) return right == null ? 0 : -1;
        return right == null ? 1 : left.compareTo(right);
    }
}
