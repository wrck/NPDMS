package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import java.util.Set;

/** 批量读取任务当前责任区间。 */
public record CurrentTaskAssignmentsQuery(Long tenantId, Set<Long> taskIds) {
}
