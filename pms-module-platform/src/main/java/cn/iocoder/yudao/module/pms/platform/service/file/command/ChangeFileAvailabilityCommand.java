package cn.iocoder.yudao.module.pms.platform.service.file.command;

public record ChangeFileAvailabilityCommand(
        Long tenantId, Long actorUserId, String idempotencyKey,
        Long artifactId, Integer versionNo, Integer expectedAvailabilityVersion,
        String targetStatus, String reasonCode, String reasonDetail,
        String ownerContext, String objectType, String objectId,
        String purposeCode, String referenceKey) {
}
