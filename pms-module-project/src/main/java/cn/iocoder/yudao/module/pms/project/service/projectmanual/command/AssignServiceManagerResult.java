package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

/** V1人工确认服务经理结果。 */
public record AssignServiceManagerResult(
        Long projectId,
        Long assignmentId,
        Integer version,
        String assignmentStatus) {
}
