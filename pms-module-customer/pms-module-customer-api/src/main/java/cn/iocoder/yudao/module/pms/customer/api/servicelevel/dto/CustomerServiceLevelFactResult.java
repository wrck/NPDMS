package cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto;

import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactException;

/** 当前事实或锁定重验结果。 */
public record CustomerServiceLevelFactResult(Decision decision, CustomerServiceLevelFact currentFact) {

    public CustomerServiceLevelFactResult {
        if (decision == null || currentFact == null
                || (decision == Decision.AVAILABLE
                && currentFact.status() != CustomerServiceLevelFact.Status.AVAILABLE)
                || (decision == Decision.NOT_CONFIGURED
                && currentFact.status() != CustomerServiceLevelFact.Status.NOT_CONFIGURED)) {
            throw new CustomerServiceLevelFactException(
                    CustomerServiceLevelFactException.Code.OWNER_DATA_CORRUPTED,
                    "decision and current service-level fact are inconsistent");
        }
    }

    public enum Decision {
        AVAILABLE,
        NOT_CONFIGURED,
        STALE
    }
}
