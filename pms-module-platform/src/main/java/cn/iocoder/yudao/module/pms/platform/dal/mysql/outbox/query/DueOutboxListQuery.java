package cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query;

import lombok.Builder;

import java.time.LocalDateTime;

/** 到期Outbox锁定查询。 */
@Builder
public record DueOutboxListQuery(
        Long tenantId,
        String eventType,
        LocalDateTime dueAt,
        int limit) {
}
