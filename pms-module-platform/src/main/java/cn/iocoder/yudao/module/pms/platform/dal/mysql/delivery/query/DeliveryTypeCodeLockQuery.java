package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;
/** Tenant-scoped current catalog fact locked until upload completion commits. */
public record DeliveryTypeCodeLockQuery(Long tenantId, String typeCode) { }
