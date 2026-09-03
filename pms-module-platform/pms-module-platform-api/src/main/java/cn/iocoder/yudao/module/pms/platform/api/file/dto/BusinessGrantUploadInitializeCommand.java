package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record BusinessGrantUploadInitializeCommand(
        Long tenantId, Long grantId, Integer grantVersion, Long questionnaireId,
        String requestId, Long responseId, String policyKey, String operationId,
        String fileName, String categoryCode, Long declaredSizeBytes,
        String declaredMediaType, String clientSha256) {
}
