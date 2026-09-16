package cn.iocoder.yudao.module.pms.platform.api.entity;

/** Stable business identity; does not require a current row or version registration. */
public record EntityRef(Long tenantId, String ownerModule, String entityType, Long entityId) {
    public EntityRef {
        if (tenantId == null || tenantId < 0 || entityId == null || entityId <= 0
                || ownerModule == null || ownerModule.isBlank() || entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("Complete business entity identity is required");
        }
    }
}
