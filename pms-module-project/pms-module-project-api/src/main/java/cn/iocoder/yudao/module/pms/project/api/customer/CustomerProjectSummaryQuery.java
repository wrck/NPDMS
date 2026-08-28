package cn.iocoder.yudao.module.pms.project.api.customer;

public record CustomerProjectSummaryQuery(
        Long tenantId,
        Long customerId,
        Long subjectUserId,
        Integer pageNo,
        Integer pageSize) {
}
