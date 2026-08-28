package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

/** 完成命令锁后读取当前执行契约。 */
public record CurrentTaskExecutionContractLockQuery(Long tenantId, Long projectTaskId) {
}
