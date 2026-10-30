package cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query;

/** 项目 + 任务编码维度的模板冻结要求锁定查询（任务初始化/重命名绑定锁）。 */
public record DeliveryRequirementTaskLockQuery(Long tenantId, Long projectId, String taskCode) {
}
