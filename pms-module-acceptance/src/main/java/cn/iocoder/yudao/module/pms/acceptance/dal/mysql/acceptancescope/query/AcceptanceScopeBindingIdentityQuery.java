package cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancescope.query;

public record AcceptanceScopeBindingIdentityQuery(
        Long tenantId,
        Long projectId,
        Long projectStageSnapshotId,
        Long deliveryScopeId,
        Long scopeAllocationVersion) {
}
