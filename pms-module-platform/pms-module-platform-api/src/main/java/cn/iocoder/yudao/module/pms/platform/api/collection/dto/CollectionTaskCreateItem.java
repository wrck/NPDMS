package cn.iocoder.yudao.module.pms.platform.api.collection.dto;

public record CollectionTaskCreateItem(
        String deviceId,
        String deviceName,
        String host,
        Integer port,
        String protocol,
        String templateId,
        String templateVersion,
        String templateHash,
        String credentialMode,
        Long credentialId,
        Long grantSnapshotId,
        String idempotencyKey,
        String consumerContext,
        String consumerObjectType,
        String consumerObjectId) {
}
