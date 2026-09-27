package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

public record SubmitCutoverPlanCommand(Long tenantId, Long actorId, Long taskId,
                                       Long expectedTaskVersion, Long expectedPlanVersion,
                                       String idempotencyKey, String correlationId) {
}
