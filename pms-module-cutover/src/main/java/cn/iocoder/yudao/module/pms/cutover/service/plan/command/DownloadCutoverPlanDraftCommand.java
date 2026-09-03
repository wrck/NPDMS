package cn.iocoder.yudao.module.pms.cutover.service.plan.command;

public record DownloadCutoverPlanDraftCommand(Long tenantId, Long actorId, Long taskId,
                                              Integer expectedPlanVersion, String idempotencyKey,
                                              String correlationId) {
}
