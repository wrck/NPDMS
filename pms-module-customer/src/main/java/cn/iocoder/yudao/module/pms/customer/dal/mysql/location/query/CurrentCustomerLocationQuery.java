package cn.iocoder.yudao.module.pms.customer.dal.mysql.location.query;

public record CurrentCustomerLocationQuery(
        Long tenantId,
        Long customerId,
        String locationType) {
}
