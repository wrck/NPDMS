package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

/** 受信租户内任务主键查询。 */
public record TaskByIdQuery(Long tenantId, Long taskId) {
}
