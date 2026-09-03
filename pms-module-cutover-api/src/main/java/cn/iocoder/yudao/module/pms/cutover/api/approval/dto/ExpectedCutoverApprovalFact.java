package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

public record ExpectedCutoverApprovalFact(
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

    public ExpectedCutoverApprovalFact {
        validate(approvalInstanceId, approvalVersion, taskId, planRevisionId, planRevisionNo, status,
                sourceSnapshotVersion, replacementApprovalInstanceId, rejectionReason);
    }

    static void validate(Long approvalInstanceId, Integer approvalVersion, Long taskId, Long planRevisionId,
                         Integer planRevisionNo, ApprovalStatus status, Integer sourceSnapshotVersion,
                         Long replacementApprovalInstanceId, String rejectionReason) {
        ApprovalContractRules.positive(approvalInstanceId, "approvalInstanceId");
        ApprovalContractRules.nonNegative(approvalVersion, "approvalVersion");
        ApprovalContractRules.positive(taskId, "taskId");
        ApprovalContractRules.positive(planRevisionId, "planRevisionId");
        ApprovalContractRules.positive(planRevisionNo, "planRevisionNo");
        if (status == null) {
            throw ApprovalContractRules.invalid("status is required");
        }
        ApprovalContractRules.positive(sourceSnapshotVersion, "sourceSnapshotVersion");
        ApprovalContractRules.nullablePositive(replacementApprovalInstanceId, "replacementApprovalInstanceId");
        if (rejectionReason != null) {
            ApprovalContractRules.nonBlank(rejectionReason, 1000, "rejectionReason");
        }
    }
}
