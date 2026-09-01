package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** 当前阶段及其相邻下一阶段查询。 */
public record ProjectStagePairForUpdateQuery(Long tenantId, Long projectId, String currentStageCode) {
}
