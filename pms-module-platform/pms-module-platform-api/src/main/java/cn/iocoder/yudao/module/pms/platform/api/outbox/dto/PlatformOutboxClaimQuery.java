package cn.iocoder.yudao.module.pms.platform.api.outbox.dto;

import java.time.LocalDateTime;

/** 到期Outbox批次查询。 */
public record PlatformOutboxClaimQuery(String eventType, LocalDateTime dueAt, int limit) {

    public PlatformOutboxClaimQuery(LocalDateTime dueAt, int limit) {
        this("ProjectServiceManagerAssigned", dueAt, limit);
    }
}
