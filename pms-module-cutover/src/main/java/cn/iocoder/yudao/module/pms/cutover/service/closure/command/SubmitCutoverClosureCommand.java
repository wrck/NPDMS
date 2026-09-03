package cn.iocoder.yudao.module.pms.cutover.service.closure.command;

public record SubmitCutoverClosureCommand(Long tenantId, Long actorId, Long taskId,
                                          Integer expectedTaskVersion, Long closureId,
                                          Integer expectedClosureVersion, String finalResult,
                                          String idempotencyKey, String correlationId) {
}
