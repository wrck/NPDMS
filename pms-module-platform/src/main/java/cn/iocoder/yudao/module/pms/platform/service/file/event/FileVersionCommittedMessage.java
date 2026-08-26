package cn.iocoder.yudao.module.pms.platform.service.file.event;

import java.time.LocalDateTime;

public record FileVersionCommittedMessage(
        String eventId,
        Long tenantId,
        Long artifactId,
        Integer versionNo,
        String sha256,
        String scanStatus,
        LocalDateTime occurredAt,
        String operationId) {
}
