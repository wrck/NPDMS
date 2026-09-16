package cn.iocoder.yudao.module.pms.platform.api.entity;

import java.util.List;
import java.util.Map;

/** Public behavior only; registering an ordinary entity does not enable versioning. */
public interface EntityVersionApi {
    List<EntityVersionProvider.Revision> history(EntityRef entity, EntityActor actor, Long beforeId, int limit);
    EntityVersionProvider.Revision create(EntityRef entity, RevisionRef source, String reason, EntityActor actor);
    EntityVersionProvider.Revision save(RevisionRef ref, int expectedVersion, Map<String, Object> fields, EntityActor actor);
    EntityVersionProvider.Revision complete(RevisionRef ref, int expectedVersion, EntityActor actor);
    List<FieldDifference> compare(RevisionRef left, RevisionRef right, EntityActor actor);

    record FieldDifference(String fieldCode, EntityFieldValue before, EntityFieldValue after) {}
}
