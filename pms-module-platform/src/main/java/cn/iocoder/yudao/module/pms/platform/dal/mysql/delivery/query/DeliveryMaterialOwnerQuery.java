package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;
public record DeliveryMaterialOwnerQuery(Long tenantId, String ownerModule, String entityType, Long entityId, String typeCode) {}
