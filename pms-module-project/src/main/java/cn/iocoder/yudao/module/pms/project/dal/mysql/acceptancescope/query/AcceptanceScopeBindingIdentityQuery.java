package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancescope.query;

public record AcceptanceScopeBindingIdentityQuery(
        Long tenantId,
        Long projectId,
        Long projectStageSnapshotId,
        Long deliveryScopeId,
        Long scopeAllocationVersion) {
}
