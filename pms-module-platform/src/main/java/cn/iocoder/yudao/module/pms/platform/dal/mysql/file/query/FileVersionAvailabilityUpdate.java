package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

import java.time.LocalDateTime;

public record FileVersionAvailabilityUpdate(
        Long tenantId, Long artifactId, Integer versionNo,
        Integer expectedAvailabilityVersion, String expectedStatus,
        String targetStatus, String reasonCode, LocalDateTime occurredAt) {
}
