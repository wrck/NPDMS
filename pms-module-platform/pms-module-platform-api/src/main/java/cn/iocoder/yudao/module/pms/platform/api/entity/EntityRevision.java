package cn.iocoder.yudao.module.pms.platform.api.entity;

import java.time.LocalDateTime;

/**
 * Implemented by a business revision entity, independently of its business superclass.
 * No current-entity requirements, persistence annotations or draft-count policy belong here.
 */
public interface EntityRevision {
    Long getId();
    Long getTenantId();
    Long getEntityId();
    Integer getRevisionNo();
    Long getSourceRevisionId();
    Long getBaseEffectiveRevisionId();
    Integer getBaseEntityVersion();
    Integer getVersion();
    String getChangeReason();
    Long getFrozenBy();
    LocalDateTime getFrozenAt();

    EntityRef entityRef();
    EntityVersionProvider.Revision.State revisionState();
    boolean effective();

    default RevisionRef revisionRef() {
        EntityRef entity = entityRef();
        if (!entity.tenantId().equals(getTenantId()) || !entity.entityId().equals(getEntityId())) {
            throw new IllegalStateException("Revision identity differs from the owning business entity");
        }
        return new RevisionRef(entity, getId());
    }

    default EntityVersionProvider.Revision revisionMetadata() {
        return new EntityVersionProvider.Revision(revisionRef(), getRevisionNo(), getSourceRevisionId(),
                getBaseEffectiveRevisionId(), getBaseEntityVersion(), revisionState(), effective(),
                getVersion(), getChangeReason(), getFrozenBy(), getFrozenAt());
    }
}
