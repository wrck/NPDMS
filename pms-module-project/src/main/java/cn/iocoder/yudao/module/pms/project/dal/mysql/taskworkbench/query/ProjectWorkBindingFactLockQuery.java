package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 按受信租户、项目与任务锁定同一当前执行契约。 */
public record ProjectWorkBindingFactLockQuery(
        Long tenantId,
        Long projectId,
        Long projectTaskId) {
}
