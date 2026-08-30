package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record AuthenticatedAssistedUploadInitializeCommand(
        Long tenantId, Long taskId, Long questionnaireId, String requestId, Long responseId,
        String policyKey, String operationId, String fileName, String categoryCode,
        Long declaredSizeBytes, String declaredMediaType, String clientSha256) {
}
