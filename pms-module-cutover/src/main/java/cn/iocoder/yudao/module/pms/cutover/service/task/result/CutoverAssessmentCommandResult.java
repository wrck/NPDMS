package cn.iocoder.yudao.module.pms.cutover.service.task.result;

public record CutoverAssessmentCommandResult(Long taskId, Long assessmentId, Long assessmentVersion,
                                              Long assessmentRowVersion, Long taskVersion,
                                              String status) {
}
