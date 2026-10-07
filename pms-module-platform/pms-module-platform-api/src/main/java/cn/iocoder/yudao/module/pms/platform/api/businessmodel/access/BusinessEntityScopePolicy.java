package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import java.util.List;

/** Owner data scope shared by direct model reads and bounded queries. */
public interface BusinessEntityScopePolicy {
    boolean supports(String ownerModule, String entityType);
    void requireReadable(EntityRef entity, EntityActor actor);
    List<BusinessFieldFilter> queryScope(EntityActor actor);
}
