package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

import java.time.LocalDateTime;

public record FileArtifactLifecycleUpdate(
        Long tenantId, Long artifactId, Integer expectedVersion,
        String expectedStatus, String targetStatus, String reasonCode,
        String reasonDetail, Long actorUserId, LocalDateTime occurredAt,
        boolean logicalDelete) {
}
