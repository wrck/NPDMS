package cn.iocoder.yudao.module.pms.cutover.service.task.command;

import java.time.LocalDateTime;

/** 单次业务变更的服务端操作者、关联标识和统一发生时间。 */
public record CutoverOperationContext(Long actorId, String correlationId, LocalDateTime occurredAt) {
}
