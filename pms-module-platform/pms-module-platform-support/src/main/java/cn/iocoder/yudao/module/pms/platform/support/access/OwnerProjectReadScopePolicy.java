package cn.iocoder.yudao.module.pms.platform.support.access;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import java.util.*;

/** Explicit legacy read binding. It neither supplies operation permissions nor participates in writes. */
public final class OwnerProjectReadScopePolicy implements BusinessEntityScopePolicy {
    private final Set<String> models;
    private final String ownershipField;
    private final BusinessModelCatalog catalog;
    private final BusinessEntityPersistenceRegistry persistence;
    private final BusinessScopeAccess projects;
    public OwnerProjectReadScopePolicy(Set<String> models, String ownershipField, BusinessModelCatalog catalog,
            BusinessEntityPersistenceRegistry persistence, BusinessScopeAccess projects) {
        this.models=Set.copyOf(models);this.ownershipField=ownershipField;
        this.catalog=catalog;this.persistence=persistence;this.projects=projects;
    }
    @Override public boolean supports(String owner, String type) {
        return models.contains(owner+"/"+type) && catalog.require(owner,type).scopeBinding()==null;
    }
    @Override public List<BusinessFieldFilter> queryScope(EntityActor actor) {
        var allowed=projects.readableScopeIds(actor);
        return List.of(new BusinessFieldFilter(ownershipField,BusinessFieldFilter.Operator.IN,
                allowed==null?List.of():new ArrayList<Object>(allowed)));
    }
    @Override public void requireReadable(EntityRef entity, EntityActor actor) {
        actor.requireTenant(entity);
        var declaration=persistence.require(entity.ownerModule(),entity.entityType());
        var row=persistence.<BaseBusinessEntity>mapperOf(declaration).selectById(entity.entityId());
        if(row==null || !actor.tenantId().equals(row.getTenantId()))
            throw new BusinessContractException("ENTITY_NOT_FOUND","Business object is unavailable");
        Object value="id".equals(ownershipField)?row.getId():BusinessModelIntrospector.readValues(row,
                BusinessModelIntrospector.businessFields(declaration.entityClass())).get(ownershipField);
        if(!(value instanceof Long id) || id<=0)
            throw new BusinessContractException("SCOPE_ID_INVALID","Invalid Owner project reference: "+ownershipField);
        projects.requireReadable(id,actor);
    }
}
