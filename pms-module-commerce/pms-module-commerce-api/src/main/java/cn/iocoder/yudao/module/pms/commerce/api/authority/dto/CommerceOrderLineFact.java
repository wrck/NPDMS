package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityContractRules.*;

public record CommerceOrderLineFact(String sourceKey, String expectedPreviousSourceVersion,
                                    String sourceVersion, String salesOrderSourceKey,
                                    String lineCode, String itemCode, String modelCode,
                                    BigDecimal quantity, String unitCode,
                                    CommerceSourceLifecycleStatus lifecycleStatus,
                                    LocalDateTime sourceUpdatedAt) {

    public CommerceOrderLineFact {
        sourceKey = text(sourceKey, 128, "sourceKey");
        expectedPreviousSourceVersion = expectedVersion(expectedPreviousSourceVersion);
        sourceVersion = version(sourceVersion, "sourceVersion");
        salesOrderSourceKey = text(salesOrderSourceKey, 128, "salesOrderSourceKey");
        lineCode = text(lineCode, 64, "lineCode");
        itemCode = optionalText(itemCode, 128, "itemCode");
        modelCode = optionalText(modelCode, 64, "modelCode");
        quantity = nonNegative(quantity, "quantity");
        unitCode = optionalText(unitCode, 32, "unitCode");
        if (lifecycleStatus == null) {
            throw invalid("lifecycleStatus must not be null");
        }
        sourceUpdatedAt = time(sourceUpdatedAt, "sourceUpdatedAt");
    }
}
