package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

import cn.iocoder.yudao.module.pms.commerce.api.scope.DeliveryScopeFactException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 一个qualified范围明细，不聚合不同产品、型号或地点。 */
public record AssignedDeliveryScopeLine(Long scopeId, Long scopeDetailId, Long orderLineId,
                                        BigDecimal quantity, String unitCode, String productCode,
                                        String modelCode, List<String> serialNumbers) {

    public AssignedDeliveryScopeLine {
        requirePositive(scopeId, "scopeId");
        requirePositive(scopeDetailId, "scopeDetailId");
        requirePositive(orderLineId, "orderLineId");
        if (quantity == null || quantity.signum() <= 0) {
            throw corrupted("quantity must be positive");
        }
        unitCode = requireText(unitCode, 32, "unitCode");
        productCode = optionalText(productCode, 64, "productCode");
        modelCode = optionalText(modelCode, 64, "modelCode");
        if (productCode == null && modelCode == null) {
            throw corrupted("productCode or modelCode is required");
        }
        if (serialNumbers == null || serialNumbers.stream().anyMatch(value -> value == null)) {
            throw corrupted("serialNumbers must be a complete list");
        }
        Set<String> comparisonKeys = new HashSet<>();
        serialNumbers = serialNumbers.stream().map(AssignedDeliveryScopeLine::serial)
                .peek(value -> {
                    if (!comparisonKeys.add(comparisonKey(value))) {
                        throw corrupted("duplicate normalized serialNumber");
                    }
                })
                .sorted(Comparator.comparing(AssignedDeliveryScopeLine::comparisonKey))
                .toList();
        if (!serialNumbers.isEmpty()
                && quantity.compareTo(BigDecimal.valueOf(serialNumbers.size())) != 0) {
            throw corrupted("quantity must equal serialNumbers size");
        }
    }

    private static String serial(String value) {
        return requireText(value, 128, "serialNumber");
    }

    private static String comparisonKey(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String optionalText(String value, int maxLength, String field) {
        return value == null ? null : requireText(value, maxLength, field);
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw corrupted(field + " must be nonblank and at most " + maxLength + " characters");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw corrupted(field + " must be nonblank and at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw corrupted(field + " must be positive");
        }
    }

    private static DeliveryScopeFactException corrupted(String message) {
        return new DeliveryScopeFactException(DeliveryScopeFactException.Code.OWNER_DATA_CORRUPTED, message);
    }
}
