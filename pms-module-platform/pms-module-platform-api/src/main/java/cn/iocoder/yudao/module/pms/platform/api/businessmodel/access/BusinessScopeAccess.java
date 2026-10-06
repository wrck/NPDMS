package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import java.util.Set;

/** One scope implementation per domain policy, shared by every declared entity. */
public interface BusinessScopeAccess {
    String policyRef();
    Set<Long> readableScopeIds(EntityActor actor);
    void requireReadable(Long scopeId, EntityActor actor);
    void requireWritable(Long scopeId, EntityActor actor, boolean lock);

    /** Validate all affected scopes together so domain policies can order their underlying locks. */
    default void requireWritableScopes(Set<Long> scopeIds, EntityActor actor, boolean lock) {
        scopeIds.stream().sorted().forEach(id -> requireWritable(id, actor, lock));
    }
}
