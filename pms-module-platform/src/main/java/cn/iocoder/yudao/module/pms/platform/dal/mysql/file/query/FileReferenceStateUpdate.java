package cn.iocoder.yudao.module.pms.platform.dal.mysql.file.query;

import java.time.LocalDateTime;

public record FileReferenceStateUpdate(
        Long tenantId, Long referenceId, Integer expectedVersion,
        String expectedStatus, String targetStatus, Long scopeVersion,
        Long actorUserId, String reason, LocalDateTime occurredAt) {
}
