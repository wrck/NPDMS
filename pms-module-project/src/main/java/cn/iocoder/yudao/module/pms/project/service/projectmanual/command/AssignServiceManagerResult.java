package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

import java.time.LocalDateTime;

/** V1.8人工确认服务经理结果。 */
public record AssignServiceManagerResult(
        Long projectId,
        Long assignmentId,
        Integer version,
        String assignmentStatus,
        LocalDateTime effectiveFrom,
        Long previousPrimaryManagerId,
        Long currentPrimaryManagerId) {
}
