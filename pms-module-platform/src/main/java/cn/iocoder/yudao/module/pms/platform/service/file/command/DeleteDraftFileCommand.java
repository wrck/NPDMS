package cn.iocoder.yudao.module.pms.platform.service.file.command;

public record DeleteDraftFileCommand(
        Long tenantId, Long actorUserId, String idempotencyKey,
        Long artifactId, Integer expectedArtifactVersion,
        String ownerContext, String objectType, String objectId,
        String purposeCode, String referenceKey, String reason) {
}
