package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.query;

/** ACC交付件根的阶段门禁锁查询。 */
public record AccProjectDeliverableGateFactQuery(Long tenantId, Long projectId, String deliverableCode) {
}
