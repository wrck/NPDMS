package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

public record DeliveryEvidenceRetryUpdate(
        Long tenantId,
        Long evidenceId,
        Integer currentRevision,
        Integer expectedVersion,
        String expectedStatus,
        String targetStatus,
        Integer expectedRetryCount,
        Integer newRetryCount,
        LocalDateTime nextRetryAt,
        String eventId,
        LocalDateTime publishedAt) {
}
