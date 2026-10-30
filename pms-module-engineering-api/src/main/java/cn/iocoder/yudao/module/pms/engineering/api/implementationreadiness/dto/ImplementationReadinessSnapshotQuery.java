package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto;

/** 精确读取已被业务引用的不可变修订；不触发重新评估。 */
public record ImplementationReadinessSnapshotQuery(Long tenantId, Long projectId, Long snapshotId, Long snapshotVersion) {
    public ImplementationReadinessSnapshotQuery {
        ImplementationReadinessQuery.requirePositive(tenantId, "tenantId");
        ImplementationReadinessQuery.requirePositive(projectId, "projectId");
        ImplementationReadinessQuery.requirePositive(snapshotId, "snapshotId");
        if (snapshotVersion == null || snapshotVersion < 0) throw ImplementationReadinessQuery.invalid("snapshotVersion must be non-negative");
    }
}
