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
                                     String salesType, String sourceProjectName, String orderComment, LocalDateTime orderCreateTime, LocalDateTime customerRequiredTime,
                                     String customInfo, String contractNo, String executionNo, Long expectedRowVersion) {

    public CommerceSalesOrderFact(String sourceKey, String expectedPreviousSourceVersion,
                                     String sourceVersion, String companyCode, String orderNo,
                                     String orderType, String customerCode, String customerName,
                                     BigDecimal amount, String currencyCode,
                                     CommerceSourceLifecycleStatus lifecycleStatus,
                                     LocalDateTime sourceUpdatedAt) {
        this(sourceKey, expectedPreviousSourceVersion, sourceVersion, companyCode, orderNo, orderType, customerCode, customerName, amount, currencyCode, lifecycleStatus, sourceUpdatedAt, null, null, null, null, null, null, null, null);
    }

    public CommerceSalesOrderFact(String sourceKey, String expectedPreviousSourceVersion,
                                     String sourceVersion, String companyCode, String orderNo,
                                     String orderType, String customerCode, String customerName,
                                     BigDecimal amount, String currencyCode,
                                     CommerceSourceLifecycleStatus lifecycleStatus,
                                     LocalDateTime sourceUpdatedAt,
                                     String salesType, String sourceProjectName, String orderComment, LocalDateTime orderCreateTime, LocalDateTime customerRequiredTime) {
        this(sourceKey, expectedPreviousSourceVersion, sourceVersion, companyCode, orderNo, orderType, customerCode, customerName, amount, currencyCode, lifecycleStatus, sourceUpdatedAt, salesType, sourceProjectName, orderComment, orderCreateTime, customerRequiredTime, null, null, null);
    }

    /** Legacy callers retain source-version CAS; corrections can additionally guard the row they read. */
    public CommerceSalesOrderFact(String sourceKey, String expectedPreviousSourceVersion,
                                  String sourceVersion, String companyCode, String orderNo,
                                  String orderType, String customerCode, String customerName,
                                  BigDecimal amount, String currencyCode,
                                  CommerceSourceLifecycleStatus lifecycleStatus, LocalDateTime sourceUpdatedAt,
                                  String salesType, String sourceProjectName, String orderComment,
                                  LocalDateTime orderCreateTime, LocalDateTime customerRequiredTime,
                                  String customInfo, String contractNo, String executionNo) {
        this(sourceKey, expectedPreviousSourceVersion, sourceVersion, companyCode, orderNo, orderType,
                customerCode, customerName, amount, currencyCode, lifecycleStatus, sourceUpdatedAt,
                salesType, sourceProjectName, orderComment, orderCreateTime, customerRequiredTime,
                customInfo, contractNo, executionNo, null);
    }

    public CommerceSalesOrderFact {
        sourceKey = text(sourceKey, 128, "sourceKey");
        expectedPreviousSourceVersion = expectedVersion(expectedPreviousSourceVersion);
        if (expectedRowVersion != null && expectedRowVersion < 0) {
            throw invalid("expectedRowVersion must be non-negative");
        }
        sourceVersion = version(sourceVersion, "sourceVersion");
        companyCode = text(companyCode, 64, "companyCode");
        orderNo = text(orderNo, 64, "orderNo");
        orderType = text(orderType, 32, "orderType");
        customerCode = optionalText(customerCode, 64, "customerCode");
        customerName = originalOrderText(customerName, 512, "customerName",sourceKey);
        amount = nonNegative(amount, "amount");
        currencyCode = optionalText(currencyCode, 32, "currencyCode");
        if (lifecycleStatus == null) {
            throw invalid("lifecycleStatus must not be null");
        }
        salesType = optionalText(salesType, 32, "salesType");
        sourceProjectName = originalOrderText(sourceProjectName, 512, "sourceProjectName",sourceKey);
        orderComment = originalOrderText(orderComment, 2048, "orderComment",sourceKey);
        // customInfo 为来源自定义载荷原样透传：不截断、不参与Owner载荷裁决（见 AuthorityPayloadCanonicalizer）。
        customInfo = passthrough(customInfo);
        sourceUpdatedAt = time(sourceUpdatedAt, "sourceUpdatedAt");
        contractNo = optionalText(contractNo, 64, "contractNo");
        executionNo = optionalText(executionNo, 64, "executionNo");
    }
}
