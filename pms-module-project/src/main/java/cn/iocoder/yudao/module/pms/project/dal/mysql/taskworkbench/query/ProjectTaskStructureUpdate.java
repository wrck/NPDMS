package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 任务移动的邻接与闭包更新参数。 */
public record ProjectTaskStructureUpdate(
        Long tenantId,
        Long projectId,
        Long taskId,
        Long targetParentTaskId,
        Integer expectedTaskVersion,
        Long newRootTaskId,
        Integer newTreeDepth,
        Integer depthDelta,
        String updater) {
}
