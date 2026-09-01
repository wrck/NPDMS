package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

public record CutoverApprovalInspectResult(InspectStatus status, CutoverApprovalFact fact) {

    public CutoverApprovalInspectResult {
        if (status == null || status == InspectStatus.FOUND && fact == null
                || status == InspectStatus.NOT_FOUND && fact != null) {
            throw ApprovalContractRules.invalid("inspect result is inconsistent");
        }
    }
}
