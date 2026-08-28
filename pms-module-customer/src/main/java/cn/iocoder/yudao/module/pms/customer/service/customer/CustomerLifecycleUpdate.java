package cn.iocoder.yudao.module.pms.customer.service.customer;

public record CustomerLifecycleUpdate(
        Long tenantId,
        Long customerId,
        String expectedStatus,
        String targetStatus,
        boolean expectedDeleted,
        boolean targetDeleted,
        Long expectedVersion) {
}
