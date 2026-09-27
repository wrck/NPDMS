package cn.iocoder.yudao.module.pms.cutover.service.plan.result;

public record InvalidateCutoverPlanSourceResult(Long taskId, String taskStage, Long taskVersion,
                                                 Long planRevisionId, Long planVersion, String planStatus,
                                                 Long approvalInstanceId, Long approvalVersion,
                                                 String approvalStatus) {
}
