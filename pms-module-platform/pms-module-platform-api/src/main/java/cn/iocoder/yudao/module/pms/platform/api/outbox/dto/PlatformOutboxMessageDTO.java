package cn.iocoder.yudao.module.pms.platform.api.outbox.dto;

import java.time.LocalDateTime;

/** 待投递Outbox消息。 */
public record PlatformOutboxMessageDTO(
        String eventId,
        String eventType,
        String payload,
        int retryCount,
        Long tenantId,
        LocalDateTime occurredAt) {
}
