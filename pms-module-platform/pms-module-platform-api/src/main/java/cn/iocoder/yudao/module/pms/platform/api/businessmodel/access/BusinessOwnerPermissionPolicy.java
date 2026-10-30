package cn.iocoder.yudao.module.pms.platform.api.businessmodel.access;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;

/** Owner functional permission for catalog metadata. Project/state/runtime checks remain in the command. */
public interface BusinessOwnerPermissionPolicy {
    String ownerModule();
    String entityType();
    boolean readable(EntityActor actor);
    boolean executable(EntityActor actor, String operationCode, int operationVersion);
}
