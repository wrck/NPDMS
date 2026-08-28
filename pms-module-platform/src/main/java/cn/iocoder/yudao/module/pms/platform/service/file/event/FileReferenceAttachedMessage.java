package cn.iocoder.yudao.module.pms.platform.service.file.event;

import java.time.LocalDateTime;

public record FileReferenceAttachedMessage(
        String eventId,
        Long tenantId,
        Long referenceId,
        Long artifactId,
        Integer versionNo,
        String ownerContext,
        String objectType,
        String objectId,
        String purposeCode,
        LocalDateTime occurredAt,
        String operationId) {
}
