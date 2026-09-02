package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

public record CutoverApprovalRevalidationResult(RevalidationStatus status, CutoverApprovalFact currentFact) {

    public CutoverApprovalRevalidationResult {
        if (status == null || currentFact == null) {
            throw ApprovalContractRules.invalid("revalidation result is incomplete");
        }
    }
}
