package cn.iocoder.yudao.module.pms.platform.api.entity;

import java.util.Objects;

/** Exactly one data location: the current object, or a specific business revision. */
public record EntityDataRef(EntityRef entity, Long revisionId) {
    public EntityDataRef {
        Objects.requireNonNull(entity, "entity");
        if (revisionId != null && revisionId <= 0) throw new IllegalArgumentException("Invalid revisionId");
    }

    public static EntityDataRef current(EntityRef entity) { return new EntityDataRef(entity, null); }
    public static EntityDataRef revision(RevisionRef revision) {
        return new EntityDataRef(revision.entity(), revision.revisionId());
    }
    public boolean isRevision() { return revisionId != null; }
}
