package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

public record CutoverTaskAssessmentLinkUpdate(Long tenantId, Long taskId, Integer expectedVersion,
                                               Long assessmentId) {
}
