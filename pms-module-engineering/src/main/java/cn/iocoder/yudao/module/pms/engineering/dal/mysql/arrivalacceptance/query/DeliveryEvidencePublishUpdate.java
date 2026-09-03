package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

public record DeliveryEvidencePublishUpdate(
        Long tenantId,
        Long evidenceId,
        Integer expectedRevision,
        Integer expectedVersion,
        String eventId,
        String correlationId,
        Long actorUserId,
        LocalDateTime publishedAt) {
}
