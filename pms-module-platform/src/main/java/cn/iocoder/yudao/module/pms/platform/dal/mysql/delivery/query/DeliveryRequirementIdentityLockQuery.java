package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

/** 要求唯一身份锁定查询（tenant + owner 三元组 + type_code，对应 uk_plt_delivery_req）。 */
public record DeliveryRequirementIdentityLockQuery(Long tenantId, String ownerModule, String entityType,
                                                   Long entityId, String typeCode) {
}
