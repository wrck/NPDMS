package cn.iocoder.yudao.module.pms.platform.dal.mysql.export.query;

import java.time.LocalDateTime;

public record ExportTaskStatusUpdate(Long tenantId, Long taskId, Integer expectedVersion,
                                     String expectedStatus, String targetStatus,
                                     Long resultCount, Long artifactId, Integer fileVersionNo,
                                     String referenceKey, Integer artifactVersion, Integer referenceVersion,
                                     Integer availabilityVersion, String fileHash, LocalDateTime expiresAt,
                                     String failureCode, Boolean failureRetryable, String updater) {
}
