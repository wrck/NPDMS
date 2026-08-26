package cn.iocoder.yudao.module.pms.asset.api.customer;

public record CustomerDeviceSummaryQuery(
        Long tenantId,
        Long customerId,
        Integer pageNo,
        Integer pageSize) {
}
