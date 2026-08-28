package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

public record CollectionTaskReconciliationUpdate(
        Long tenantId,
        String platformTaskId,
        String expectedStatus,
        Long expectedLastCallbackSequence,
        String technicalStage) {
}
