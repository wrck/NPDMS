package cn.iocoder.yudao.module.pms.engineering.api.arrival.event;

import java.time.LocalDateTime;

/** ACC 归档 EXE-01 到货签收证据 revision 的回执。 */
public record ArtifactArchivedMessage(
        String eventId,
        Long tenantId,
        Long evidenceId,
        Integer evidenceRevision,
        Long artifactId,
        Integer fileVersion,
        String archiveRecordId,
        LocalDateTime occurredAt,
        String correlationId) {
}
