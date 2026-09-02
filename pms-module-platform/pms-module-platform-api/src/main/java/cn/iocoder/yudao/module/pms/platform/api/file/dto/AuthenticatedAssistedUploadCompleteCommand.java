package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record AuthenticatedAssistedUploadCompleteCommand(
        Long tenantId, Long taskId, Long questionnaireId, String requestId, Long responseId,
        String policyKey, String operationId, String fileSlotKey, Integer fileSequence,
        Long artifactId, Long sessionId, byte[] content, String clientSha256) {
    public AuthenticatedAssistedUploadCompleteCommand {
        content = content == null ? null : content.clone();
    }
    @Override public byte[] content() { return content == null ? null : content.clone(); }
}
