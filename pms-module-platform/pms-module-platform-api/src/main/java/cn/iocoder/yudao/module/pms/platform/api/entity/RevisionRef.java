package cn.iocoder.yudao.module.pms.platform.api.entity;

import java.util.Objects;

/** The revision ID is the primary key of the Owner's history table. */
public record RevisionRef(EntityRef entity, Long revisionId) {
    public RevisionRef {
        Objects.requireNonNull(entity, "entity");
        if (revisionId == null || revisionId <= 0) throw new IllegalArgumentException("revisionId is required");
    }
}
