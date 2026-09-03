package cn.iocoder.yudao.module.pms.platform.api.file.dto;

public record BusinessGrantUploadCompleteCommand(
        Long tenantId, Long grantId, Integer grantVersion, Long questionnaireId,
        String requestId, Long responseId, String policyKey, String operationId,
        String fileSlotKey, Integer fileSequence, Long artifactId, Long sessionId,
        byte[] content, String clientSha256) {

    public BusinessGrantUploadCompleteCommand {
        content = content == null ? null : content.clone();
    }

    @Override
    public byte[] content() {
        return content == null ? null : content.clone();
    }
}
