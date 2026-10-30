package cn.iocoder.yudao.module.pms.cutover.service.task.command;

public record SubmitCutoverAssessmentCommand(Long tenantId, Long actorId, Long taskId,
                                              Long expectedTaskVersion, Long expectedAssessmentVersion,
                                              String idempotencyKey, String correlationId) implements CutoverTaskMutation {
}
