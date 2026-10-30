package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

/** 要求主键锁定查询（FOR UPDATE）。 */
public record DeliveryRequirementIdLockQuery(Long tenantId, Long id) {
}
