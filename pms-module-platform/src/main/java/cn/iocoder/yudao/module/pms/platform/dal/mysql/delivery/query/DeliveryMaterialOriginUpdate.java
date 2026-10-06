package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;
public record DeliveryMaterialOriginUpdate(Long tenantId, Long materialId, String businessTypeCode,
        String ownerModule, String entityType, Long entityId, Long revisionId) {}
