package cn.iocoder.yudao.module.pms.cutover.service.plan.result;

public record SubmitCutoverPlanResult(Long taskId, String taskStage, Integer taskVersion,
                                      Long planRevisionId, Integer revisionNo, Integer planVersion,
                                      Long approvalInstanceId, Integer approvalVersion,
                                      String approvalStatus) {
}
