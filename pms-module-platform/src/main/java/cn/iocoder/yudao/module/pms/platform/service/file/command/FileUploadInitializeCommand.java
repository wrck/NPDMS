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
        String clientSha256, tools.jackson.databind.JsonNode ownerExecutionContext) {
    public FileUploadInitializeCommand(Long tenantId, Long actorUserId, String idempotencyKey, String modeCode, Long artifactId, Integer expectedReferenceVersion, String ownerContext, String objectType, String objectId, String purposeCode, String referenceKey, String fileName, String categoryCode, Long declaredSizeBytes, String declaredMediaType, String clientSha256) {
        this(tenantId, actorUserId, idempotencyKey, modeCode, artifactId, expectedReferenceVersion, ownerContext, objectType, objectId, purposeCode, referenceKey, fileName, categoryCode, declaredSizeBytes, declaredMediaType, clientSha256, null);
    }
    @Override public tools.jackson.databind.JsonNode ownerExecutionContext() {
        return ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
    public FileUploadInitializeCommand {
        ownerExecutionContext = ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
}
