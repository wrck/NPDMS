package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

public record DeliveryRequirementEntityQuery(Long tenantId, String ownerModule, String entityType, Long entityId) { }
