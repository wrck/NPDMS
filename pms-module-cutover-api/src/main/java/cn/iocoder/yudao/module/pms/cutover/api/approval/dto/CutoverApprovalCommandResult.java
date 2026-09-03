package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

public record CutoverApprovalCommandResult(CommandOutcome outcome, CutoverApprovalFact fact) {

    public CutoverApprovalCommandResult {
        if (outcome == null || fact == null) {
            throw ApprovalContractRules.invalid("command result is incomplete");
        }
    }
}
