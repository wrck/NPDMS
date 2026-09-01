package cn.iocoder.yudao.module.pms.cutover.api.approval.dto;

public record CutoverApprovalFactQuery(Long tenantId, Long taskId, Long planRevisionId) {

    public CutoverApprovalFactQuery {
        ApprovalContractRules.positive(tenantId, "tenantId");
        ApprovalContractRules.positive(taskId, "taskId");
        ApprovalContractRules.positive(planRevisionId, "planRevisionId");
    }
}
