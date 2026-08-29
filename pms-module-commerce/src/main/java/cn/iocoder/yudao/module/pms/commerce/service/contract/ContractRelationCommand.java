package cn.iocoder.yudao.module.pms.commerce.service.contract;

public record ContractRelationCommand(
        Long tenantId, Long subjectUserId, Long contractId, Long projectId,
        String relationRole, String operationId, String reason) {
}
