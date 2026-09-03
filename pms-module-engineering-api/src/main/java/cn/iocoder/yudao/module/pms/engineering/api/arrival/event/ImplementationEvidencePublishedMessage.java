package cn.iocoder.yudao.module.pms.engineering.api.arrival.event;

import java.time.LocalDateTime;

/** EXE-01 到货签收证据发布给 ACC 的不可变 revision 事实。 */
public record ImplementationEvidencePublishedMessage(
        String eventId,
        Long tenantId,
        Long evidenceId,
        Integer evidenceRevision,
        Long artifactId,
        Integer fileVersion,
        String fileReference,
        String hash,
        String sourceRequirement,
        Long sourceRecordId,
        Long sourceVersion,
        String sourceScopeWatermark,
        LocalDateTime occurredAt,
        String correlationId) {
}
