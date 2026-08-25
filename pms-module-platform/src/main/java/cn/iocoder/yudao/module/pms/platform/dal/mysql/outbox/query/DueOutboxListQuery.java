package cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;

/** 到期Outbox锁定查询。 */
@Builder
public record DueOutboxListQuery(
        Long tenantId,
        Set<String> eventTypes,
        LocalDateTime dueAt,
        int limit) {
}
