package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

public record CutoverApprovalRevalidationQuery(Long tenantId, ExpectedCutoverApprovalFact expected) {

    public CutoverApprovalRevalidationQuery {
        ApprovalContractRules.positive(tenantId, "tenantId");
        if (expected == null) {
            throw ApprovalContractRules.invalid("expected is required");
        }
    }
}
