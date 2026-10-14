package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

public record CutoverTaskAssessmentLinkUpdate(Long tenantId, Long taskId, Long expectedVersion,
                                               Long assessmentId) {
}
