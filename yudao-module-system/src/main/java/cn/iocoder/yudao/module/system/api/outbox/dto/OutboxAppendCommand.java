package cn.iocoder.yudao.module.system.api.outbox.dto;

/** 待追加的不可变 Outbox 事件。 */
public record OutboxAppendCommand(String eventId, long tenantId, String aggregateType, long aggregateId,
                                  String eventType, int eventVersion, String payloadJson) {
}
