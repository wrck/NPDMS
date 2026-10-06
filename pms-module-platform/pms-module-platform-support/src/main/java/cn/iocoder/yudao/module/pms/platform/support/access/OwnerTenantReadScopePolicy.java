package cn.iocoder.yudao.module.pms.platform.support.access;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import java.util.*;

/** Explicit Owner evidence for tenant-global read-only catalogs; never an undeclared default. */
public final class OwnerTenantReadScopePolicy implements BusinessEntityScopePolicy {
    private final Set<String> models;
    private final BusinessModelCatalog catalog;
    private final BusinessEntityPersistenceRegistry persistence;
    public OwnerTenantReadScopePolicy(Set<String> models,BusinessModelCatalog catalog,BusinessEntityPersistenceRegistry persistence) {
        this.models=Set.copyOf(models);this.catalog=catalog;this.persistence=persistence;
    }
    @Override public boolean supports(String owner,String type) {
        return models.contains(owner+"/"+type) && catalog.require(owner,type).scopeBinding()==null;
    }
    @Override public List<BusinessFieldFilter> queryScope(EntityActor actor) {
        // The shared reader always adds an exact tenant_id predicate and the declared read permission.
        return List.of();
    }
    @Override public void requireReadable(EntityRef entity,EntityActor actor) {
        actor.requireTenant(entity);
        var declaration=persistence.require(entity.ownerModule(),entity.entityType());
        var row=persistence.<BaseBusinessEntity>mapperOf(declaration).selectById(entity.entityId());
        if(row==null || !actor.tenantId().equals(row.getTenantId()))
            throw new BusinessContractException("ENTITY_NOT_FOUND","Owner catalog object is unavailable");
    }
}
