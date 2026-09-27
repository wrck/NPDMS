package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

public record CutoverTaskTransitionUpdate(Long tenantId, Long taskId, Long expectedVersion,
                                          Long assessmentId, String manualGrade,
                                          String currentStage, String taskStatus) {
}
