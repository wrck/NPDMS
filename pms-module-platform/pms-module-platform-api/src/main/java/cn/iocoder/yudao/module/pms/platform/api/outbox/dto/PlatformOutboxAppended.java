package cn.iocoder.yudao.module.pms.platform.api.outbox.dto;

import java.time.LocalDateTime;

/** In-process wakeup for a durable message. Consume only AFTER_COMMIT; never a business completion fact. */
public record PlatformOutboxAppended(PlatformOutboxMessageDTO message, LocalDateTime notBefore) { }
