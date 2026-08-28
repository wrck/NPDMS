package cn.iocoder.yudao.module.pms.platform.service.file.command;

import org.springframework.web.multipart.MultipartFile;

public record FileUploadCompleteCommand(
        Long tenantId,
        Long actorUserId,
        String idempotencyKey,
        Long artifactId,
        Long sessionId,
        MultipartFile file,
        String clientSha256) {
}
