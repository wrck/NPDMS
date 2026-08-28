package cn.iocoder.yudao.module.pms.customer.dal.mysql.customer.query;

public record CurrentCustomerMappingQuery(
        Long tenantId,
        String sourceSystem,
        String sourceKey,
        Long customerId) {

    public static CurrentCustomerMappingQuery bySource(Long tenantId, String sourceSystem, String sourceKey) {
        return new CurrentCustomerMappingQuery(tenantId, sourceSystem, sourceKey, null);
    }

    public static CurrentCustomerMappingQuery byCustomer(Long tenantId, Long customerId) {
        return new CurrentCustomerMappingQuery(tenantId, null, null, customerId);
    }
}
