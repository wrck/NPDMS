package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityContractRules.*;

public record CommerceOrderLineFact(String sourceKey, String expectedPreviousSourceVersion,
                                    String sourceVersion, String salesOrderSourceKey,
                                    String lineCode, String itemCode, String itemDescription,
                                    String productCode, String modelCode,
                                    BigDecimal orderQuantity, BigDecimal openQuantity,
                                    BigDecimal deliveredQuantity, String unitCode, Integer unitScale,
                                    String quantityStatus,
                                    CommerceSourceLifecycleStatus lifecycleStatus,
                                    LocalDateTime sourceUpdatedAt,
                                     String lineType, String bundleCode, String profitCenter, String realExecutionNo, Integer warrantyMonth) {

    public CommerceOrderLineFact(String sourceKey, String expectedPreviousSourceVersion,
                                    String sourceVersion, String salesOrderSourceKey,
                                    String lineCode, String itemCode, String itemDescription,
                                    String productCode, String modelCode,
                                    BigDecimal orderQuantity, BigDecimal openQuantity,
                                    BigDecimal deliveredQuantity, String unitCode, Integer unitScale,
                                    String quantityStatus,
                                    CommerceSourceLifecycleStatus lifecycleStatus,
                                    LocalDateTime sourceUpdatedAt) {
        this(sourceKey, expectedPreviousSourceVersion, sourceVersion, salesOrderSourceKey, lineCode, itemCode, itemDescription, productCode, modelCode, orderQuantity, openQuantity, deliveredQuantity, unitCode, unitScale, quantityStatus, lifecycleStatus, sourceUpdatedAt, null, null, null, null, null);
    }

    public CommerceOrderLineFact {
        sourceKey = text(sourceKey, 128, "sourceKey");
        expectedPreviousSourceVersion = expectedVersion(expectedPreviousSourceVersion);
        sourceVersion = version(sourceVersion, "sourceVersion");
        salesOrderSourceKey = text(salesOrderSourceKey, 128, "salesOrderSourceKey");
        lineCode = text(lineCode, 32, "lineCode");
        itemCode = optionalText(itemCode, 64, "itemCode");
        itemDescription = optionalText(itemDescription, 512, "itemDescription");
        productCode = optionalText(productCode, 64, "productCode");
        modelCode = optionalText(modelCode, 64, "modelCode");
        orderQuantity = quantity(orderQuantity, "orderQuantity");
        openQuantity = quantity(openQuantity, "openQuantity");
        deliveredQuantity = quantity(deliveredQuantity, "deliveredQuantity");
        if (unitScale == null || unitScale < 0 || unitScale > 6) {
            throw invalid("unitScale must be between 0 and 6");
        }
        unitCode = text(unitCode, 32, "unitCode");
        quantityStatus = text(quantityStatus, 32, "quantityStatus");
        if ("CONFIRMED".equals(quantityStatus) && orderQuantity == null) {
            throw invalid("confirmed quantity requires orderQuantity");
        }
        if (lifecycleStatus == null) {
            throw invalid("lifecycleStatus must not be null");
        }
        lineType = optionalText(lineType, 32, "lineType");
        bundleCode = optionalText(bundleCode, 64, "bundleCode");
        profitCenter = optionalText(profitCenter, 64, "profitCenter");
        realExecutionNo = optionalText(realExecutionNo, 64, "realExecutionNo");
        if (warrantyMonth != null && warrantyMonth < 0) throw invalid("warrantyMonth must be non-negative");
        for (BigDecimal value : new BigDecimal[]{orderQuantity, openQuantity, deliveredQuantity}) {
            if (value != null && value.stripTrailingZeros().scale() > unitScale) {
                throw invalid("source quantity exceeds the declared unitScale");
            }
        }
        if (!java.util.Set.of("PENDING_AUTHORITY", "CONFIRMED").contains(quantityStatus)) {
            throw invalid("quantityStatus is not a recognized authority status");
        }
        sourceUpdatedAt = time(sourceUpdatedAt, "sourceUpdatedAt");
    }

    public CommerceOrderLineFact(String sourceKey, String expectedPreviousSourceVersion,
                                 String sourceVersion, String salesOrderSourceKey,
                                 String lineCode, String itemCode, String modelCode,
                                 BigDecimal quantity, String unitCode,
                                 CommerceSourceLifecycleStatus lifecycleStatus,
                                 LocalDateTime sourceUpdatedAt) {
        this(sourceKey, expectedPreviousSourceVersion, sourceVersion, salesOrderSourceKey,
                lineCode, itemCode, null, null, modelCode, quantity, quantity, BigDecimal.ZERO,
                unitCode, quantity == null ? 0 : Math.max(0, quantity.stripTrailingZeros().scale()),
                quantity == null ? "PENDING_AUTHORITY" : "CONFIRMED", lifecycleStatus, sourceUpdatedAt);
    }

    public BigDecimal quantity() {
        return orderQuantity;
    }

    private static BigDecimal quantity(BigDecimal value, String field) {
        value = nonNegative(value, field);
        if (value != null && (value.scale() > 6 || value.precision() - value.scale() > 12)) {
            throw invalid(field + " exceeds decimal(18,6)");
        }
        return value;
    }
}
