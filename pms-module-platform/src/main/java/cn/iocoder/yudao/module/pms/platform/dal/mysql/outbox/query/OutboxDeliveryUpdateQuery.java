package cn.iocoder.yudao.module.pms.platform.dal.mysql.outbox.query;

/** Outbox投递完成CAS条件。 */
public record OutboxDeliveryUpdateQuery(Long tenantId, String eventId, int expectedRetryCount) {
}
