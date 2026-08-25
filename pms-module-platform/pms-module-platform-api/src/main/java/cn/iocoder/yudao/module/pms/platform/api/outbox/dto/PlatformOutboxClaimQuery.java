package cn.iocoder.yudao.module.pms.platform.api.outbox.dto;

import java.time.LocalDateTime;
import java.util.Set;

/** 到期Outbox批次查询。 */
public record PlatformOutboxClaimQuery(LocalDateTime dueAt, int limit, Set<String> eventTypes) {

    public PlatformOutboxClaimQuery {
        eventTypes = eventTypes == null ? null : Set.copyOf(eventTypes);
    }
}
