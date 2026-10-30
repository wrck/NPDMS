package cn.iocoder.yudao.module.pms.platform.api.entity;

/**
 * 可信操作者：由服务端从会话或系统契约构造，HTTP 请求不可注入。
 * userId=0 表示系统观察者（执行后端等平台内部消费），correlationId 必须为
 * SYSTEM_FACT_OBSERVER；真实用户仍要求 userId>0。
 */
public record EntityActor(Long tenantId, Long userId, String correlationId) {
    public static final String SYSTEM_OBSERVER = "SYSTEM_FACT_OBSERVER";

    public EntityActor {
        if (tenantId == null || tenantId < 0) {
            throw new IllegalArgumentException("Tenant is required");
        }
        boolean systemObserver = userId != null && userId == 0L;
        if (systemObserver && !SYSTEM_OBSERVER.equals(correlationId)) {
            throw new IllegalArgumentException("System observer requires the reserved correlation id");
        }
        if (!systemObserver && (userId == null || userId <= 0)) {
            throw new IllegalArgumentException("Tenant and actor are required");
        }
    }

    public boolean isSystemObserver() {
        return userId != null && userId == 0L;
    }

    public void requireTenant(EntityRef entity) {
        if (!tenantId.equals(entity.tenantId())) throw new IllegalArgumentException("Entity tenant mismatch");
    }
}
