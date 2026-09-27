package cn.iocoder.yudao.module.pms.cutover.service.taskv2.command;

public record SubmitCutoverAssessmentCommand(Long tenantId, Long actorId, Long taskId,
                                              Long expectedTaskVersion, Long expectedAssessmentVersion,
                                              String idempotencyKey, String correlationId) {
}
