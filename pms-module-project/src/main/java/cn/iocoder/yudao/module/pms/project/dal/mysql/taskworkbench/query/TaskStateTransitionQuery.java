package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 冻结状态机版本中的稳定动作查询。 */
public record TaskStateTransitionQuery(
        Long tenantId,
        Long revisionId,
        String fromStatusCode,
        String actionCode) {
}
