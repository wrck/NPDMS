package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

/** V1人工确认服务经理命令。 */
public record AssignServiceManagerCommand(
        Long projectId,
        Integer expectedVersion,
        String levelCode,
        Long managerId,
        Long siteId,
        String assignmentType,
        Long departmentId,
        String departmentCode,
        String changeReason,
        String idempotencyKey,
        String requestDigest) {
}
