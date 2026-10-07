package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;

/** Shared bridge for native Owner content, preserving current/revision identity and authorization. */
public interface BusinessEntityContentReader {
    boolean supports(String ownerModule, String entityType);
    BusinessEntityData read(EntityDataRef target, EntityActor actor);
}
