package cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto;

public record AcceptanceScopeBindingFact(
        Long bindingId,
        Long projectStageSnapshotId,
        Long deliveryScopeId,
        Long scopeAllocationVersion,
        String bindingTrigger,
        Integer acceptanceFactVersion) {
}
