package cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto;

public record EffectiveScopeBindingCommand(
        Long tenantId,
        Long projectId,
        Long projectStageSnapshotId,
        Long deliveryScopeId,
        Long scopeAllocationVersion,
        String operationId) {
}
