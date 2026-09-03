package cn.iocoder.yudao.module.pms.platform.controller.admin.export.vo;

import java.time.LocalDateTime;

public record ExportTaskRespVO(Long taskId, String ownerContext, String exportType, String status,
                               boolean failureRetryable, Integer retryCount, Integer version,
                               Long resultCount, Long artifactId, Integer fileVersionNo,
                               LocalDateTime expiresAt) {
}
