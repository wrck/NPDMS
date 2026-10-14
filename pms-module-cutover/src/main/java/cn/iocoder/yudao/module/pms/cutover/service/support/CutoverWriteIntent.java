package cn.iocoder.yudao.module.pms.cutover.service.support;

/** 一次用户写入意图；重试沿用相同 key，关联标识不参与业务载荷摘要。 */
public record CutoverWriteIntent(String idempotencyKey, String correlationId) { }
