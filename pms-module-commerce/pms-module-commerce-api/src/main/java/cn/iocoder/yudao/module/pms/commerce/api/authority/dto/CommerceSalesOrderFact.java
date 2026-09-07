package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityContractRules.*;

public record CommerceSalesOrderFact(String sourceKey, String expectedPreviousSourceVersion,
                                     String sourceVersion, String companyCode, String orderNo,
                                     String orderType, String customerCode, String customerName,
                                     BigDecimal amount, String currencyCode,
                                     CommerceSourceLifecycleStatus lifecycleStatus,
                                     LocalDateTime sourceUpdatedAt,
                                     String salesType, String sourceProjectName, String orderComment, LocalDateTime orderCreateTime, LocalDateTime customerRequiredTime) {

    public CommerceSalesOrderFact(String sourceKey, String expectedPreviousSourceVersion,
                                     String sourceVersion, String companyCode, String orderNo,
                                     String orderType, String customerCode, String customerName,
                                     BigDecimal amount, String currencyCode,
                                     CommerceSourceLifecycleStatus lifecycleStatus,
                                     LocalDateTime sourceUpdatedAt) {
        this(sourceKey, expectedPreviousSourceVersion, sourceVersion, companyCode, orderNo, orderType, customerCode, customerName, amount, currencyCode, lifecycleStatus, sourceUpdatedAt, null, null, null, null, null);
    }

    public CommerceSalesOrderFact {
        sourceKey = text(sourceKey, 128, "sourceKey");
        expectedPreviousSourceVersion = expectedVersion(expectedPreviousSourceVersion);
        sourceVersion = version(sourceVersion, "sourceVersion");
        companyCode = text(companyCode, 64, "companyCode");
        orderNo = text(orderNo, 64, "orderNo");
        orderType = text(orderType, 32, "orderType");
        customerCode = optionalText(customerCode, 64, "customerCode");
        customerName = optionalText(customerName, 512, "customerName");
        amount = nonNegative(amount, "amount");
        currencyCode = optionalText(currencyCode, 32, "currencyCode");
        if (lifecycleStatus == null) {
            throw invalid("lifecycleStatus must not be null");
        }
        salesType = optionalText(salesType, 32, "salesType");
        sourceProjectName = optionalText(sourceProjectName, 512, "sourceProjectName");
        orderComment = optionalText(orderComment, 2048, "orderComment");
        sourceUpdatedAt = time(sourceUpdatedAt, "sourceUpdatedAt");
    }
}
