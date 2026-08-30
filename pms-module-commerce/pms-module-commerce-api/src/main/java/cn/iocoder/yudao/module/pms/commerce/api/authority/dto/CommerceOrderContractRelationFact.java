package cn.iocoder.yudao.module.pms.commerce.api.authority.dto;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.module.pms.commerce.api.authority.dto.CommerceAuthorityContractRules.*;

public record CommerceOrderContractRelationFact(String salesOrderSourceKey,
                                                String contractSourceKey,
                                                String expectedPreviousSourceVersion,
                                                String sourceVersion,
                                                LocalDateTime effectiveFrom,
                                                LocalDateTime effectiveTo) {

    public CommerceOrderContractRelationFact {
        salesOrderSourceKey = text(salesOrderSourceKey, 128, "salesOrderSourceKey");
        contractSourceKey = text(contractSourceKey, 128, "contractSourceKey");
        expectedPreviousSourceVersion = expectedVersion(expectedPreviousSourceVersion);
        sourceVersion = version(sourceVersion, "sourceVersion");
        effectiveFrom = time(effectiveFrom, "effectiveFrom");
        if (effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw invalid("effectiveTo must be after effectiveFrom");
        }
    }
}
