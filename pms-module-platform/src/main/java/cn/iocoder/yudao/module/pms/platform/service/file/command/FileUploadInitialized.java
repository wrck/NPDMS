package cn.iocoder.yudao.module.pms.platform.service.file.command;

import java.time.LocalDateTime;

public record FileUploadInitialized(Long artifactId, Long sessionId, LocalDateTime expiresAt) {
}
