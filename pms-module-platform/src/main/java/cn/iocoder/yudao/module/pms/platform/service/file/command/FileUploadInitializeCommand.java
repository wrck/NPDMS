package cn.iocoder.yudao.module.pms.platform.service.file.command;

public record FileUploadInitializeCommand(
        Long tenantId,
        Long actorUserId,
        String idempotencyKey,
        String modeCode,
        Long artifactId,
        Integer expectedReferenceVersion,
        String ownerContext,
        String objectType,
        String objectId,
        String purposeCode,
        String referenceKey,
        String fileName,
        String categoryCode,
        Long declaredSizeBytes,
        String declaredMediaType,
        String clientSha256) {
}
