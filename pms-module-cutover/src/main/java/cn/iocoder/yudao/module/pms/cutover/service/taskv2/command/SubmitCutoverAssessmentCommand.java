package cn.iocoder.yudao.module.pms.cutover.service.taskv2.command;

public record SubmitCutoverAssessmentCommand(Long tenantId, Long actorId, Long taskId,
                                              Integer expectedTaskVersion, Integer expectedAssessmentVersion,
                                              String idempotencyKey, String correlationId) {
}
