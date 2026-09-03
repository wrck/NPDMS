package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

import java.time.LocalDateTime;

public record CutoverAssessmentSubmitUpdate(Long tenantId, Long assessmentId, Integer expectedVersion,
                                             String contextSnapshot, String manualGrade, boolean simpleFlow,
                                             Long submittedBy, LocalDateTime submittedAt) {
}
