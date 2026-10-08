package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
/** Shared capability/reference protection; called in the transaction holding the current Owner lock. */
public interface BusinessDeletionGuard {
    void requireDeletable(EntityRef entity, EntityActor actor);
}
