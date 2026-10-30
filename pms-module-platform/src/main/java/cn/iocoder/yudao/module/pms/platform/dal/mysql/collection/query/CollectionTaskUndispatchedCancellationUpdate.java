package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

/** Apply a provider's durable pre-submission cancellation, without overwriting accepted execution facts. */
public record CollectionTaskUndispatchedCancellationUpdate(
        Long tenantId, String platformTaskId, String expectedStatus, String expectedTechnicalStage) {
}
