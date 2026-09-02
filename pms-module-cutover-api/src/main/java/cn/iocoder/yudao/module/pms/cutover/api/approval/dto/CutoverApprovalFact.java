package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

public record CutoverApprovalFact(
        Long approvalInstanceId,
        Integer approvalVersion,
        Long taskId,
        Long planRevisionId,
        Integer planRevisionNo,
        ApprovalStatus status,
        Integer sourceSnapshotVersion,
        Long replacementApprovalInstanceId,
        Long decisionAt,
        String rejectionReason) {

    public CutoverApprovalFact {
        ExpectedCutoverApprovalFact.validate(approvalInstanceId, approvalVersion, taskId, planRevisionId,
                planRevisionNo, status, sourceSnapshotVersion, replacementApprovalInstanceId, rejectionReason);
    }
}
