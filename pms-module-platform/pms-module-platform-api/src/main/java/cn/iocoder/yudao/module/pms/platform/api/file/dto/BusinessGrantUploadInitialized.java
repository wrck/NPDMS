package cn.iocoder.yudao.module.pms.platform.api.file.dto;

import java.time.LocalDateTime;

public record BusinessGrantUploadInitialized(
        Long responseId, String fileSlotKey, Integer fileSequence,
        Long artifactId, Long sessionId, Long scopeVersion, LocalDateTime expiresAt) {
}
