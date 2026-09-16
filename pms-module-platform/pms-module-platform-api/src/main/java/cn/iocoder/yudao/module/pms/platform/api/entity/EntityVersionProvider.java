package cn.iocoder.yudao.module.pms.platform.api.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Optional SPI. Every persistence operation is implemented by the business Owner. */
public interface EntityVersionProvider {
    String ownerModule();
    String entityType();

    Revision inspect(RevisionRef ref, EntityActor actor);
    List<Revision> history(EntityRef entity, EntityActor actor, Long beforeRevisionId, int limit);

    /** Owner allocates max(revisionNo) + 1 under its object/scope lock. */
    Revision createDraft(EntityRef entity, RevisionRef source, String reason, EntityActor actor);
    Revision save(RevisionRef ref, Integer expectedVersion, Map<String, Object> fields, EntityActor actor);

    /** Freeze content and evidence. The enclosing Owner transaction includes extensions and files. */
    Revision freeze(RevisionRef ref, Integer expectedVersion, EntityActor actor);

    /** Revalidate the creation baseline, copy to current data and switch effective metadata atomically. */
    Revision activate(RevisionRef ref, Integer expectedVersion, EntityActor actor);

    record Revision(RevisionRef ref, int revisionNo, Long sourceRevisionId,
                    Long baseEffectiveRevisionId, Integer baseEntityVersion,
                    State state, boolean effective, int version, String reason,
                    Long frozenBy, LocalDateTime frozenAt) {
        public enum State { DRAFT, FROZEN }
    }
}
