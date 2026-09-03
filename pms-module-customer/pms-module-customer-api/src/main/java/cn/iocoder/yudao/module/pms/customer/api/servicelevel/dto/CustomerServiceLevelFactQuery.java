package cn.iocoder.yudao.module.pms.customer.api.servicelevel.dto;

import cn.iocoder.yudao.module.pms.customer.api.servicelevel.CustomerServiceLevelFactException;

/** 查询客户当前服务等级事实。 */
public record CustomerServiceLevelFactQuery(Long tenantId, Long customerId) {

    public CustomerServiceLevelFactQuery {
        requirePositive(tenantId, "tenantId");
        requirePositive(customerId, "customerId");
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new CustomerServiceLevelFactException(
                    CustomerServiceLevelFactException.Code.INVALID_REQUEST, field + " must be positive");
        }
    }
}
