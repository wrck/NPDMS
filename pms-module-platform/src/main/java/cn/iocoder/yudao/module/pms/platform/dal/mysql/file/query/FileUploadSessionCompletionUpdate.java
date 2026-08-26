package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

import java.time.LocalDateTime;

public record FileUploadSessionCompletionUpdate(
        Long tenantId,
        Long sessionId,
        Integer expectedVersion,
        Long artifactId,
        Long referenceId,
        String actualSha256,
        Integer completedFileVersionNo,
        Long registeredInfraFileId,
        LocalDateTime completedAt) {
}
