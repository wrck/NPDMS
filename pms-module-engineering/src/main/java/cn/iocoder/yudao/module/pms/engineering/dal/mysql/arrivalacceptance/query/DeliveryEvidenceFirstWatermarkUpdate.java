package cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.query;

import java.time.LocalDateTime;

public record DeliveryEvidenceFirstWatermarkUpdate(
        Long tenantId,
        Long evidenceId,
        Integer currentRevision,
        Integer expectedVersion,
        String eventId,
        LocalDateTime nextRetryAt) {
}
