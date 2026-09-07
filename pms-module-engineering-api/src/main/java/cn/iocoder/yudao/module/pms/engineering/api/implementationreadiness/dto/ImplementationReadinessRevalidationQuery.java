package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto;

import java.util.List;

public record ImplementationReadinessRevalidationQuery(
        Long tenantId,
        Long projectId,
        Long expectedSnapshotId,
        Long expectedSnapshotVersion,
        List<ImplementationReadinessQuery.ExpectedDevice> expectedDevices) {

    public ImplementationReadinessRevalidationQuery {
        ImplementationReadinessQuery.requirePositive(tenantId, "tenantId");
        ImplementationReadinessQuery.requirePositive(projectId, "projectId");
        ImplementationReadinessQuery.requirePositive(expectedSnapshotId, "expectedSnapshotId");
        if (expectedSnapshotVersion == null || expectedSnapshotVersion < 0) {
            throw ImplementationReadinessQuery.invalid("expectedSnapshotVersion must be non-negative");
        }
        ImplementationReadinessQuery normalized = new ImplementationReadinessQuery(
                tenantId, projectId, expectedDevices);
        expectedDevices = normalized.expectedDevices();
    }
}
