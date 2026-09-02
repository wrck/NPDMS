package cn.iocoder.yudao.module.pms.platform.api.export;

import java.time.LocalDateTime;

public record ExportTaskFact(
        Long taskId,
        String ownerContext,
        String exportType,
        String status,
        boolean failureRetryable,
        Integer retryCount,
        Integer version,
        Long resultCount,
        Long artifactId,
        Integer fileVersionNo,
        LocalDateTime expiresAt) {
}
