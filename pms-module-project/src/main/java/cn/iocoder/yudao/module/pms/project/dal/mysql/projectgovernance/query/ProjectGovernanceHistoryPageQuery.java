package cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query;

/** 项目异常治理动作历史稳定分页查询。 */
public record ProjectGovernanceHistoryPageQuery(
        Long tenantId,
        Long projectId,
        int offset,
        int limit) {
}
