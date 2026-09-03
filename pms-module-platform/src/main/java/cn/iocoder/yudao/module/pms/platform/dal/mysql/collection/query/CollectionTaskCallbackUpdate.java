package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query;

public record CollectionTaskCallbackUpdate(
        Long tenantId,
        String platformTaskId,
        String expectedStatus,
        Long expectedLastCallbackSequence,
        String status,
        String technicalStage,
        String externalStatus,
        Long resultVersion,
        Long fileVersionId,
        String quarantineEvidenceId,
        String failureCategory,
        Long lastCallbackSequence) {
}
