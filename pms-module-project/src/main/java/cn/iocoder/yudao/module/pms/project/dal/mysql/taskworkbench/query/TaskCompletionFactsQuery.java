package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** TASK_NATIVE完成判定的项目内结构事实查询。 */
public record TaskCompletionFactsQuery(Long tenantId, Long projectId, Long projectTaskId) {
}
