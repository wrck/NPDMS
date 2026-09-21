package cn.iocoder.yudao.module.pms.platform.service.collection;

/** Local wake-up only; the existing transactional Outbox remains the durable delivery authority. */
public record CollectionLogReady(Long tenantId, String platformTaskId) { }
