package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

public record DeliveryEvidenceAcceptedUpdate(
        Long tenantId,
        Long evidenceId,
        Integer currentRevision,
        Long expectedVersion,
        String reviewRecordId,
        String eventId,
        Integer retryCount,
        LocalDateTime nextRetryAt) {
}
