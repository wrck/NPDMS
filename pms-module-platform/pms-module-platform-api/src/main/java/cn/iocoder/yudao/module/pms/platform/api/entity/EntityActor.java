package cn.iocoder.yudao.module.pms.platform.api.entity;

public record EntityActor(Long tenantId, Long userId, String correlationId) {
    public EntityActor {
        if (tenantId == null || tenantId < 0 || userId == null || userId <= 0) {
            throw new IllegalArgumentException("Tenant and actor are required");
        }
    }

    public void requireTenant(EntityRef entity) {
        if (!tenantId.equals(entity.tenantId())) throw new IllegalArgumentException("Entity tenant mismatch");
    }
}
