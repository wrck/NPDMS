package cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto;

import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactException;

/** 在调用方写事务中锁定重验冻结的客户服务等级事实。 */
public record CustomerServiceLevelFactRevalidationQuery(
        Long tenantId,
        Long customerId,
        ExpectedCustomerServiceLevelFact expectedFact) {

    public CustomerServiceLevelFactRevalidationQuery {
        if (tenantId == null || tenantId <= 0 || customerId == null || customerId <= 0 || expectedFact == null
                || !tenantId.equals(expectedFact.tenantId()) || !customerId.equals(expectedFact.customerId())) {
            throw new CustomerServiceLevelFactException(
                    CustomerServiceLevelFactException.Code.INVALID_REQUEST,
                    "trusted tenant/customer and expected fact identity must match");
        }
    }
}
