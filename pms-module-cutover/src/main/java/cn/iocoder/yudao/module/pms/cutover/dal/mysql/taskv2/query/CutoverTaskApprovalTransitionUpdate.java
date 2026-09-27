package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

public record CutoverTaskApprovalTransitionUpdate(Long tenantId, Long taskId, Long expectedVersion,
                                                   String targetStage, String targetStatus) {
}
