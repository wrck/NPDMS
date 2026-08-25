package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 任务移动锁定范围。 */
public record ProjectTaskMoveLockQuery(
        Long tenantId,
        Long projectId,
        Long sourceTaskId,
        Long targetParentTaskId) {
}
