package cn.iocoder.yudao.module.pms.cutover.service.task.command;

import cn.iocoder.yudao.module.pms.cutover.service.task.domain.CutoverAssessmentAnswers;

public record SaveCutoverAssessmentCommand(Long tenantId, Long actorId, Long taskId,
                                            Long expectedTaskVersion, Long expectedAssessmentVersion,
                                            CutoverAssessmentAnswers answers, String manualGrade,
                                            String idempotencyKey, String correlationId) implements CutoverTaskMutation {
}
