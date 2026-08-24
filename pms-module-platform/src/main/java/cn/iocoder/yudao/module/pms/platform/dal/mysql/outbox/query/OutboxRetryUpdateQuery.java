package cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query;

import java.time.LocalDateTime;

/** Outbox重试CAS条件。 */
public record OutboxRetryUpdateQuery(
        Long tenantId,
        String eventId,
        int expectedRetryCount,
        LocalDateTime nextRetryTime) {
}
