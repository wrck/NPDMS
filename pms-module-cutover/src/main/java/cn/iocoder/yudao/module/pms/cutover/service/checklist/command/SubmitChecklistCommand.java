package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

public record SubmitChecklistCommand(Long tenantId, Long actorId, Long taskId,
                                     Long expectedTaskVersion, Long expectedAssessmentVersion,
                                     Long checklistId,
                                     Long expectedChecklistVersion, Long expectedProjectScopeVersion,
                                     String idempotencyKey, String correlationId) {
}
