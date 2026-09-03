package cn.iocoder.yudao.module.pms.cutover.service.taskv2.result;

public record CutoverAssessmentCommandResult(Long taskId, Long assessmentId, Integer assessmentVersion,
                                              Integer assessmentRowVersion, Integer taskVersion,
                                              String status) {
}
