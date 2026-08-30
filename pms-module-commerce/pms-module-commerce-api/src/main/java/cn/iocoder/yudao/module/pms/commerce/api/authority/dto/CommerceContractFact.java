package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityContractRules.*;

public record CommerceContractFact(String sourceKey, String expectedPreviousSourceVersion,
                                   String sourceVersion, String companyCode, String contractNo,
                                   String customerCode, String customerName, BigDecimal amount,
                                   String currencyCode, CommerceSourceLifecycleStatus lifecycleStatus,
                                   LocalDateTime sourceUpdatedAt) {

    public CommerceContractFact {
        sourceKey = text(sourceKey, 128, "sourceKey");
        expectedPreviousSourceVersion = expectedVersion(expectedPreviousSourceVersion);
        sourceVersion = version(sourceVersion, "sourceVersion");
        companyCode = text(companyCode, 32, "companyCode");
        contractNo = text(contractNo, 64, "contractNo");
        customerCode = optionalText(customerCode, 64, "customerCode");
        customerName = optionalText(customerName, 255, "customerName");
        amount = nonNegative(amount, "amount");
        currencyCode = optionalText(currencyCode, 16, "currencyCode");
        if (lifecycleStatus == null) {
            throw invalid("lifecycleStatus must not be null");
        }
        sourceUpdatedAt = time(sourceUpdatedAt, "sourceUpdatedAt");
    }
}
