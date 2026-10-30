package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

/**
 * DAC 终态观察：任务进入 RECONCILING 停泊并记录外部状态原值，
 * 等待证据文件与回调接入切片完成正式结果回填；不改业务状态。
 */
public record CollectionTaskReconciliationObservationUpdate(
        Long tenantId,
        String platformTaskId,
        String expectedStatus,
        String expectedTechnicalStage,
        String externalStatus) {
}
