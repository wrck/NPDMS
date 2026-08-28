package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** 完成命令锁后读取指定项目门禁。 */
public record ProjectGateForUpdateQuery(Long tenantId, Long projectId, String gateCode) {
}
