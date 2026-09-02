package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

public record CutoverApprovalStartResult(StartOutcome outcome, CutoverApprovalFact fact) {

    public CutoverApprovalStartResult {
        if (outcome == null || fact == null) {
            throw ApprovalContractRules.invalid("start result is incomplete");
        }
    }
}
