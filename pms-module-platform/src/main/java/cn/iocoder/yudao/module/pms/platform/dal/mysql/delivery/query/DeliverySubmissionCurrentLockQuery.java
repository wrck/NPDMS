package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

/** 要求当前提交锁定查询（FOR UPDATE，status=CURRENT 行）。 */
public record DeliverySubmissionCurrentLockQuery(Long tenantId, Long requirementId) {
}
