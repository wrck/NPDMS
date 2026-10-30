package cn.iocoder.yudao.module.pms.platform.api.entity;

import java.util.List;
import java.util.Map;

/**
 * Implemented by the business Owner, including entities that have no version support.
 * All reads and writes retain the Owner's authorization and business lifecycle rules.
 */
public interface EntityFieldProvider {
    String ownerModule();
    String entityType();
    default String formUsage() { return entityType(); }
    List<EntityField> fields();
    Map<String, EntityFieldValue> read(EntityDataRef target, EntityActor actor);
    /** Optional Owner concurrency metadata when native scene queries replace generic persistence reads. */
    default Long concurrencyBasis(EntityDataRef target, EntityActor actor) { return null; }

    /** Ordinary defaults save a validated patch; native draft Owners retain their snapshot semantics. */
    default boolean usesValidatedExtensionPatch() { return false; }

    /** Lock the Owner object first, verify identity, permissions, lifecycle and concurrency. */
    void lockForWrite(EntityDataRef target, EntityActor actor, Long expectedVersion);

    /** Verify current read permission and, for a revision, its membership in this entity. */
    void requireReadable(EntityDataRef target, EntityActor actor);
}
