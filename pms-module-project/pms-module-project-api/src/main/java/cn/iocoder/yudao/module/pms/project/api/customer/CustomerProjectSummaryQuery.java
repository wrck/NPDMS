package cn.iocoder.yudao.module.pms.project.api.customer;

public record CustomerProjectSummaryQuery(
        Long tenantId,
        Long customerId,
        Integer pageNo,
        Integer pageSize) {
}
