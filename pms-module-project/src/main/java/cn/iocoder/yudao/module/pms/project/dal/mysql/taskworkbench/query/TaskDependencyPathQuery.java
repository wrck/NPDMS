package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 任务依赖可达路径查询。 */
public record TaskDependencyPathQuery(
        Long tenantId,
        Long projectId,
        Long fromTaskId,
        Long toTaskId) {
}
