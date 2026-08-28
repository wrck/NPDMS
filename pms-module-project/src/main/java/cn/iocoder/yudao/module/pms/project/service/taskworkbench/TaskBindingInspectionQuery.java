package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

public record TaskBindingInspectionQuery(Long tenantId, Long taskId, Long actorId, String correlationId) {
}
