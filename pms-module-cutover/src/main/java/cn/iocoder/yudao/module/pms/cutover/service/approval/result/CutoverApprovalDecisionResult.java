package cn.iocoder.yudao.module.pms.cutover.service.approval.result;

public record CutoverApprovalDecisionResult(Long tenantId, Long approvalInstanceId, Integer approvalVersion,
                                             Long taskId, Integer taskVersion, Long planRevisionId,
                                             Integer sourceSnapshotVersion, String approvalStatus,
                                             String holdReason, Integer decidedNodeNo, Integer currentNodeNo,
                                             String taskStage, String taskStatus,
                                             Long decisionAt) {
}
