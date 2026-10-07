package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

public record DeliverySourceBusinessQuery(Long tenantId, Long projectId, String objectType,
                                         String objectId, Long revisionNo) {}
