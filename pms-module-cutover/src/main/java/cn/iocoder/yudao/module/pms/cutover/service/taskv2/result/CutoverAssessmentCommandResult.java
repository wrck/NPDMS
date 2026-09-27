package cn.iocoder.yudao.module.pms.cutover.service.taskv2.result;

public record CutoverAssessmentCommandResult(Long taskId, Long assessmentId, Long assessmentVersion,
                                              Long assessmentRowVersion, Long taskVersion,
                                              String status) {
}
