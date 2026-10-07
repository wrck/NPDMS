package cn.iocoder.yudao.module.pms.platform.support.access;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.*;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelCatalog;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.support.model.BusinessModelIntrospector;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;

/** Read-only parent ownership through the parent's public, authorized model access. */
public final class OwnerParentReadScopePolicy implements BusinessEntityScopePolicy {
    private final Set<String> models;
    private final String parentField, parentOwner, parentType;
    private final BusinessModelCatalog catalog;
    private final BusinessEntityPersistenceRegistry persistence;
    private final ObjectProvider<BusinessEntityAccessPort> access;
    public OwnerParentReadScopePolicy(Set<String> models, String parentField, String parentOwner, String parentType,
            BusinessModelCatalog catalog, BusinessEntityPersistenceRegistry persistence, ObjectProvider<BusinessEntityAccessPort> access) {
        this.models=Set.copyOf(models);this.parentField=parentField;this.parentOwner=parentOwner;this.parentType=parentType;
        this.catalog=catalog;this.persistence=persistence;this.access=access;
    }
    @Override public boolean supports(String owner, String type) {
        return models.contains(owner+"/"+type) && catalog.require(owner,type).scopeBinding()==null;
    }
    @Override public List<BusinessFieldFilter> queryScope(EntityActor actor) {
        Set<Long> ids=new LinkedHashSet<>();Set<String> visited=new HashSet<>();String cursor=null;
        while(true) {
            var page=access.getObject().query(new BusinessEntityPageQuery("owner-scope",parentOwner,parentType,List.of(),200,cursor),actor);
            if(page.completeness()==Completeness.UNAVAILABLE)
                throw new BusinessContractException("OWNER_SCOPE_UNAVAILABLE","Parent Owner scope is unavailable");
            page.members().forEach(row->ids.add(row.ref().entityId()));
            String next=page.nextCursor();
            if(next==null) break;
            if(!visited.add(next)) throw new BusinessContractException("OWNER_SCOPE_UNAVAILABLE","Parent scope cursor did not advance");
            cursor=next;
        }
        return List.of(new BusinessFieldFilter(parentField,BusinessFieldFilter.Operator.IN,new ArrayList<Object>(ids)));
    }
    @Override public void requireReadable(EntityRef entity, EntityActor actor) {
        actor.requireTenant(entity);var declaration=persistence.require(entity.ownerModule(),entity.entityType());
        var row=persistence.<BaseBusinessEntity>mapperOf(declaration).selectById(entity.entityId());
        if(row==null || !actor.tenantId().equals(row.getTenantId()))
            throw new BusinessContractException("ENTITY_NOT_FOUND","Business object is unavailable");
        Object value=BusinessModelIntrospector.readValues(row,BusinessModelIntrospector.businessFields(declaration.entityClass())).get(parentField);
        if(!(value instanceof Long id) || id<=0) throw new BusinessContractException("SCOPE_ID_INVALID","Invalid parent reference");
        var parent=access.getObject().read(EntityDataRef.current(new EntityRef(actor.tenantId(),parentOwner,parentType,id)),actor,"owner-scope");
        if(!parent.available()) throw new BusinessContractException("ENTITY_SCOPE_DENIED","Parent Owner object is unavailable");
    }
}
