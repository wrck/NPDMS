package cn.iocoder.yudao.module.pms.cutover.service.taskv2.command;

import cn.iocoder.yudao.module.pms.cutover.service.taskv2.domain.CutoverAssessmentAnswers;

public record SaveCutoverAssessmentCommand(Long tenantId, Long actorId, Long taskId,
                                            Long expectedTaskVersion, Long expectedAssessmentVersion,
                                            CutoverAssessmentAnswers answers, String manualGrade,
                                            String correlationId) {
}
