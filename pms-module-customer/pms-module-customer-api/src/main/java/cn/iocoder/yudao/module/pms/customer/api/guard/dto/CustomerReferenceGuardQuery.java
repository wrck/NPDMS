package cn.iocoder.yudao.module.pms.customer.api.guard.dto;

public record CustomerReferenceGuardQuery(
        Long tenantId,
        Long customerId) {
}
