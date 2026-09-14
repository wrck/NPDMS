package cn.iocoder.yudao.module.pms.platform.service.file.command;

import org.springframework.web.multipart.MultipartFile;

public record FileUploadCompleteCommand(
        Long tenantId,
        Long actorUserId,
        String idempotencyKey,
        Long artifactId,
        Long sessionId,
        MultipartFile file,
        String clientSha256, tools.jackson.databind.JsonNode ownerExecutionContext) {
    public FileUploadCompleteCommand(Long tenantId, Long actorUserId, String idempotencyKey, Long artifactId, Long sessionId, MultipartFile file, String clientSha256) {
        this(tenantId, actorUserId, idempotencyKey, artifactId, sessionId, file, clientSha256, null);
    }
    @Override public tools.jackson.databind.JsonNode ownerExecutionContext() {
        return ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
    public FileUploadCompleteCommand {
        ownerExecutionContext = ownerExecutionContext == null ? null : ownerExecutionContext.deepCopy();
    }
}
