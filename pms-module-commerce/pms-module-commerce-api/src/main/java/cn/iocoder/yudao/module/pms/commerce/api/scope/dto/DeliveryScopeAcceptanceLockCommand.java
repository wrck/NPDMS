package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

public record DeliveryScopeAcceptanceLockCommand(
        Long tenantId,
        Long projectId,
        Long projectStageSnapshotId,
        String operationId) {
}
