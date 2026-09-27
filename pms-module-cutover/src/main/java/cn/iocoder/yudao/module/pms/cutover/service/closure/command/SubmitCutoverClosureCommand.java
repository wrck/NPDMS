package cn.iocoder.yudao.module.pms.cutover.service.closure.command;

public record SubmitCutoverClosureCommand(Long tenantId, Long actorId, Long taskId,
                                          Long expectedTaskVersion, Long closureId,
                                          Long expectedClosureVersion, String finalResult,
                                          String idempotencyKey, String correlationId) {
}
