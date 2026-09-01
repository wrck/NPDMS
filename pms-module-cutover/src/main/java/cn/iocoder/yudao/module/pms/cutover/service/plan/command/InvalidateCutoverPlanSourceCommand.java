package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

public record InvalidateCutoverPlanSourceCommand(Long tenantId, Long actorId, Long taskId,
                                                  Integer expectedTaskVersion, Integer expectedPlanVersion,
                                                  String idempotencyKey, String correlationId) {
}
