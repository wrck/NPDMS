package cn.iocoder.yudao.module.pms.cutover.service.plan.result;

public record SubmitCutoverPlanResult(Long taskId, String taskStage, Long taskVersion,
                                      Long planRevisionId, Integer revisionNo, Long planVersion,
                                      Long approvalInstanceId, Long approvalVersion,
                                      String approvalStatus) {
}
