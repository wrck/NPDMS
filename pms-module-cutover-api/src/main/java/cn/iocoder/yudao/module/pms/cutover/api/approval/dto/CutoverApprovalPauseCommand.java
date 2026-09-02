package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

public record CutoverApprovalPauseCommand(
        Long tenantId,
        Long approvalInstanceId,
        Integer expectedApprovalVersion,
        Long planRevisionId,
        Integer expectedSourceSnapshotVersion,
        String reasonCode,
        String idempotencyKey,
        String correlationId) {

    public CutoverApprovalPauseCommand {
        ApprovalContractRules.positive(tenantId, "tenantId");
        ApprovalContractRules.positive(approvalInstanceId, "approvalInstanceId");
        ApprovalContractRules.nonNegative(expectedApprovalVersion, "expectedApprovalVersion");
        ApprovalContractRules.positive(planRevisionId, "planRevisionId");
        ApprovalContractRules.positive(expectedSourceSnapshotVersion, "expectedSourceSnapshotVersion");
        if (!"SOURCE_FACT_INVALIDATED".equals(reasonCode)) {
            throw ApprovalContractRules.invalid("reasonCode is invalid");
        }
        ApprovalContractRules.normalized(idempotencyKey, 128, "idempotencyKey");
        ApprovalContractRules.normalized(correlationId, 128, "correlationId");
    }
}
