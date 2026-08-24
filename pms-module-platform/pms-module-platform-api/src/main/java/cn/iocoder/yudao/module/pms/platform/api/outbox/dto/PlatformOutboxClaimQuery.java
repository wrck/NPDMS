package cn.iocoder.yudao.module.pms.platform.api.outbox.dto;

import java.time.LocalDateTime;

/** 到期Outbox批次查询。 */
public record PlatformOutboxClaimQuery(LocalDateTime dueAt, int limit) {
}
