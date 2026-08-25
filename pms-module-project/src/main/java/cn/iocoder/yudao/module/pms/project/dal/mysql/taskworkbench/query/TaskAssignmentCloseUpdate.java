package cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query;

import java.time.LocalDateTime;

/** 当前任务责任区间关闭 CAS。 */
public record TaskAssignmentCloseUpdate(
        Long tenantId,
        Long assignmentId,
        Integer expectedVersion,
        LocalDateTime effectiveTo,
        String updater) {
}
