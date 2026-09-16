package cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query;

import cn.iocoder.yudao.module.pms.platform.api.entity.EntityDataRef;

/** Exact current/revision value location, including tenant scope. */
public record EntityValueQuery(Long tenantId, String ownerModule, String entityType, Long entityId, Long revisionId) {
    public static EntityValueQuery of(EntityDataRef target) {
        var entity = target.entity();
        return new EntityValueQuery(entity.tenantId(), entity.ownerModule(), entity.entityType(), entity.entityId(),
                target.revisionId() == null ? 0L : target.revisionId());
    }
}
