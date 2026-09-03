package cn.iocoder.yudao.module.pms.cutover.service.checklist.command;

public record SubmitChecklistCommand(Long tenantId, Long actorId, Long taskId,
                                     Integer expectedTaskVersion, Integer expectedAssessmentVersion,
                                     Long checklistId,
                                     Integer expectedChecklistVersion, Long expectedProjectScopeVersion,
                                     String idempotencyKey, String correlationId) {
}
