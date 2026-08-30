package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

public record CutoverAssessmentDraftUpdate(Long tenantId, Long assessmentId, Integer expectedVersion,
                                            String answerSnapshot, String contextSnapshot,
                                            String manualGrade) {
}
