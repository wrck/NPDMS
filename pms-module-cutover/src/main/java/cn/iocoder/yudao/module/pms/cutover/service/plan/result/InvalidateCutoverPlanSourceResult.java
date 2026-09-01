package cn.iocoder.yudao.module.pms.cutover.service.plan.result;

public record InvalidateCutoverPlanSourceResult(Long taskId, String taskStage, Integer taskVersion,
                                                 Long planRevisionId, Integer planVersion, String planStatus,
                                                 Long approvalInstanceId, Integer approvalVersion,
                                                 String approvalStatus) {
}
