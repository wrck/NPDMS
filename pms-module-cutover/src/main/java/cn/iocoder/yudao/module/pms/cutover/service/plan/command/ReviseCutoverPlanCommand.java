package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

public record ReviseCutoverPlanCommand(Long tenantId, Long actorId, Long taskId, Integer expectedTaskVersion,
                                       Long sourcePlanRevisionId, String reason, String idempotencyKey,
                                       String correlationId) {
}
