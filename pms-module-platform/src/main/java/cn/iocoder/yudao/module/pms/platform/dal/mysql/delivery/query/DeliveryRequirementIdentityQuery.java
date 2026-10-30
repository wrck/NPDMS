package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;
/** Tenant-scoped stable requirement identity read. */
public record DeliveryRequirementIdentityQuery(Long tenantId, String ownerModule, String entityType,
                                               Long entityId, String typeCode) { }
