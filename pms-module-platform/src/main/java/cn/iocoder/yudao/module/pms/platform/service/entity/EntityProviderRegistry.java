package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.revision.InheritedRevisionAdapterFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.platform.enums.ErrorCodeConstants.ENTITY_PROVIDER_UNAVAILABLE;

@Component
public class EntityProviderRegistry {
    // Resolve lazily: business adapters also call the public extension and form APIs.
    private final ObjectProvider<EntityFieldProvider> fieldProviders;
    private final ObjectProvider<EntityVersionProvider> versionProviders;
    private final InheritedRevisionAdapterFactory inheritedRevisions;
    private final ObjectProvider<cn.iocoder.yudao.module.pms.platform.support.capability.DeclaredBusinessCapabilityAdapterFactory> declared;
    private final ObjectProvider<cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntityIdentityResolver> identities;

    public EntityProviderRegistry(ObjectProvider<EntityFieldProvider> fields, ObjectProvider<EntityVersionProvider> versions,
                                  InheritedRevisionAdapterFactory inheritedRevisions) {
        this(fields,versions,inheritedRevisions,null,null);
    }
    public EntityProviderRegistry(ObjectProvider<EntityFieldProvider> fields,ObjectProvider<EntityVersionProvider> versions,
            InheritedRevisionAdapterFactory revisions,ObjectProvider<cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntityIdentityResolver> identities) {
        this(fields,versions,revisions,identities,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public EntityProviderRegistry(ObjectProvider<EntityFieldProvider> fields, ObjectProvider<EntityVersionProvider> versions,
                                  InheritedRevisionAdapterFactory inheritedRevisions,
                                  ObjectProvider<cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntityIdentityResolver> identities,
                                  ObjectProvider<cn.iocoder.yudao.module.pms.platform.support.capability.DeclaredBusinessCapabilityAdapterFactory> declared) {
        this.fieldProviders=fields;this.versionProviders=versions;this.inheritedRevisions=inheritedRevisions;this.identities=identities;this.declared=declared;
    }
    public EntityRef nativeRef(EntityRef ref) {
        var mapping=identities == null ? null : identities.getIfAvailable();
        return mapping == null ? ref : mapping.nativeRef(ref);
    }
    public EntityDataRef nativeRef(EntityDataRef ref) { return new EntityDataRef(nativeRef(ref.entity()),ref.revisionId()); }
    public RevisionRef nativeRef(RevisionRef ref) { return new RevisionRef(nativeRef(ref.entity()),ref.revisionId()); }

    public EntityFieldProvider fields(EntityRef identity) {
        EntityRef entity=nativeRef(identity);
        var matches = fieldProviders.orderedStream().filter(provider ->
                entity.ownerModule().equals(provider.ownerModule()) && entity.entityType().equals(provider.entityType())).toList();
        if (matches.size() == 1) return matches.getFirst();
        if (matches.isEmpty() && inheritedRevisions.supports(entity)) {
            // 继承式内容历史：统一字段 Provider，避免每个普通修订实体手写实现。
            return inheritedRevisions.fieldProvider(entity);
        }
        var defaults=declared==null?null:declared.getIfAvailable();
        if(matches.isEmpty() && defaults!=null && defaults.supports(entity)) return defaults.fields(entity);
        throw exception(ENTITY_PROVIDER_UNAVAILABLE);
    }

    public EntityVersionProvider versions(EntityRef identity) {
        EntityRef entity=nativeRef(identity);
        var matches = versionProviders.orderedStream().filter(provider ->
                entity.ownerModule().equals(provider.ownerModule()) && entity.entityType().equals(provider.entityType())).toList();
        if (matches.size() == 1) return matches.getFirst();
        if (matches.isEmpty() && inheritedRevisions.supports(entity)) {
            // 继承式内容历史：通用修订实现承接 createDraft/save/freeze/activate。
            return inheritedRevisions.versionProvider(entity);
        }
        throw exception(ENTITY_PROVIDER_UNAVAILABLE);
    }

    public void requireReadable(EntityDataRef target, EntityActor actor) {
        target=nativeRef(target);
        actor.requireTenant(target.entity());
        fields(target.entity()).requireReadable(target, actor);
        if (target.isRevision()) requireRevision(target, actor);
    }

    public void lockForWrite(EntityDataRef target, EntityActor actor, Long expectedVersion) {
        target=nativeRef(target);
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
