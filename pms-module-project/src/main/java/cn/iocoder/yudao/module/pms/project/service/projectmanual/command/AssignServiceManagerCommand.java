package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

import java.time.LocalDateTime;

/** V1人工确认服务经理命令。 */
public record AssignServiceManagerCommand(
        Long projectId,
        Integer expectedVersion,
        String roleCode,
        String levelCode,
        Long managerId,
        Long siteId,
        String departmentCode,
        LocalDateTime effectiveFrom,
        String idempotencyKey,
        String requestDigest) {
}
