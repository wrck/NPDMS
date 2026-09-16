package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.ENTITY_REVISION_MISMATCH;

/** Common operation boundary; there is deliberately no public version persistence. */
@Service
@RequiredArgsConstructor
public class EntityVersionService implements EntityVersionApi {
    private final EntityProviderRegistry registry;
    private final EntityExtensionApi extensions;

    public List<EntityVersionProvider.Revision> history(EntityRef entity, EntityActor actor, Long beforeId, int limit) {
        actor.requireTenant(entity);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("History limit must be 1..100");
        return registry.versions(entity).history(entity, actor, beforeId, limit);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision create(EntityRef entity, RevisionRef source, String reason, EntityActor actor) {
        actor.requireTenant(entity);
        if (source != null && !entity.equals(source.entity())) throw exception(ENTITY_REVISION_MISMATCH);
        return registry.versions(entity).createDraft(entity, source, reason, actor);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision save(RevisionRef ref, int expectedVersion, Map<String, Object> fields, EntityActor actor) {
        registry.lockForWrite(EntityDataRef.revision(ref), actor, expectedVersion);
        return registry.versions(ref.entity()).save(ref, expectedVersion, fields, actor);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityVersionProvider.Revision complete(RevisionRef ref, int expectedVersion, EntityActor actor) {
        registry.lockForWrite(EntityDataRef.revision(ref), actor, expectedVersion);
        extensions.validateComplete(EntityDataRef.revision(ref), actor);
        var provider = registry.versions(ref.entity());
        var frozen = provider.freeze(ref, expectedVersion, actor);
        return provider.activate(ref, frozen.version(), actor);
    }

    public List<FieldDifference> compare(RevisionRef left, RevisionRef right, EntityActor actor) {
        if (!left.entity().equals(right.entity())) throw exception(ENTITY_REVISION_MISMATCH);
        var leftTarget = EntityDataRef.revision(left);
        var rightTarget = EntityDataRef.revision(right);
        registry.requireReadable(leftTarget, actor);
        registry.requireReadable(rightTarget, actor);
        var fields = registry.fields(left.entity());
        Map<String, EntityFieldValue> before = new LinkedHashMap<>(fields.read(leftTarget, actor));
        Map<String, EntityFieldValue> after = new LinkedHashMap<>(fields.read(rightTarget, actor));
        extensions.read(leftTarget, actor).fields().forEach((key, value) -> before.put(key, EntityFieldValue.known(value)));
        extensions.read(rightTarget, actor).fields().forEach((key, value) -> after.put(key, EntityFieldValue.known(value)));
        Set<String> codes = new LinkedHashSet<>(before.keySet());
        codes.addAll(after.keySet());
        List<FieldDifference> differences = new ArrayList<>();
        for (String code : codes) {
            var previous = before.getOrDefault(code, EntityFieldValue.unavailable());
            var next = after.getOrDefault(code, EntityFieldValue.unavailable());
            if (!Objects.equals(previous, next)) differences.add(new FieldDifference(code, previous, next));
        }
        return List.copyOf(differences);
    }

}
