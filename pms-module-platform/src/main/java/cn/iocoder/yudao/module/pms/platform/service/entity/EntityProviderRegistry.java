package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.ENTITY_PROVIDER_UNAVAILABLE;

@Component
@RequiredArgsConstructor
public class EntityProviderRegistry {
    // Resolve lazily: business adapters also call the public extension and form APIs.
    private final ObjectProvider<EntityFieldProvider> fieldProviders;
    private final ObjectProvider<EntityVersionProvider> versionProviders;

    public EntityFieldProvider fields(EntityRef entity) {
        var matches = fieldProviders.orderedStream().filter(provider ->
                entity.ownerModule().equals(provider.ownerModule()) && entity.entityType().equals(provider.entityType())).toList();
        if (matches.size() != 1) throw exception(ENTITY_PROVIDER_UNAVAILABLE);
        return matches.getFirst();
    }

    public EntityVersionProvider versions(EntityRef entity) {
        var matches = versionProviders.orderedStream().filter(provider ->
                entity.ownerModule().equals(provider.ownerModule()) && entity.entityType().equals(provider.entityType())).toList();
        if (matches.size() != 1) throw exception(ENTITY_PROVIDER_UNAVAILABLE);
        return matches.getFirst();
    }

    public void requireReadable(EntityDataRef target, EntityActor actor) {
        actor.requireTenant(target.entity());
        fields(target.entity()).requireReadable(target, actor);
        if (target.isRevision()) requireRevision(target, actor);
    }

    public void lockForWrite(EntityDataRef target, EntityActor actor, Integer expectedVersion) {
        actor.requireTenant(target.entity());
        if (expectedVersion == null || expectedVersion < 0) throw exception(ENTITY_PROVIDER_UNAVAILABLE);
        fields(target.entity()).lockForWrite(target, actor, expectedVersion);
        if (target.isRevision() && requireRevision(target, actor).state() != EntityVersionProvider.Revision.State.DRAFT) {
            throw exception(cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.ENTITY_REVISION_MISMATCH);
        }
    }

    private EntityVersionProvider.Revision requireRevision(EntityDataRef target, EntityActor actor) {
        var reference = new RevisionRef(target.entity(), target.revisionId());
        var revision = versions(target.entity()).inspect(reference, actor);
        if (revision == null || !reference.equals(revision.ref())) {
            throw exception(cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.ENTITY_REVISION_MISMATCH);
        }
        return revision;
    }
}
