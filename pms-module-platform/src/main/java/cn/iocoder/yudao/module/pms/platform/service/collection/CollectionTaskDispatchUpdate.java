package cn.iocoder.yudao.module.pms.platform.service.collection;

public record CollectionTaskDispatchUpdate(
        Long tenantId,
        String platformTaskId,
        String expectedTechnicalStage,
        String status,
        String technicalStage,
        String externalTaskId,
        String externalStatus,
        String failureCategory) {
}
