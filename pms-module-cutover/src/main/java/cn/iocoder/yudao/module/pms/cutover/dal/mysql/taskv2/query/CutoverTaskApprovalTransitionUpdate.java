package cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query;

public record CutoverTaskApprovalTransitionUpdate(Long tenantId, Long taskId, Integer expectedVersion,
                                                   String targetStage, String targetStatus) {
}
