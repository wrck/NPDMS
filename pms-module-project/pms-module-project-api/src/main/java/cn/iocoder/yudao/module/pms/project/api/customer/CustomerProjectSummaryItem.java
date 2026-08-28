package cn.iocoder.yudao.module.pms.project.api.customer;

public record CustomerProjectSummaryItem(
        Long projectId,
        String projectCode,
        String projectName,
        String status) {
}
