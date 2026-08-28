package cn.iocoder.yudao.module.pms.asset.api.customer;

public record CustomerDeviceSummaryQuery(
        Long tenantId,
        Long customerId,
        Long subjectUserId,
        Integer pageNo,
        Integer pageSize) {
}
