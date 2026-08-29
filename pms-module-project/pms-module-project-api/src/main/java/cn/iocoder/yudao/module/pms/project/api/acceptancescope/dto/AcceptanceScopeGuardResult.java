package cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto;

public record AcceptanceScopeGuardResult(
        AcceptanceScopeGuardOutcome outcome,
        Integer acceptanceFactVersion,
        Long projectStageSnapshotId,
        Long deliveryScopeId,
        Long scopeAllocationVersion) {
}
