package cn.iocoder.yudao.module.pms.project.dal.mysql.stagegate.query;

/** PROJ本地Gate Owner稳定对象锁查询。 */
public record ProjectLocalGateFactQuery(Long tenantId, Long projectId, String ownerCode) {
}
