package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench;

/** 获权任务按阶段聚合结果。 */
public record TaskStageCount(String stageCode, long taskCount) {
}
