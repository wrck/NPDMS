package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

public record CutoverTaskApprovalTransitionUpdate(Long tenantId, Long taskId, Long expectedVersion,
                                                   String targetStage, String targetStatus) {
}
