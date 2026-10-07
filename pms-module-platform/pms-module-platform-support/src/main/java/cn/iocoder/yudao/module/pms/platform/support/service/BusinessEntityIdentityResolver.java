package cn.iocoder.yudao.module.pms.platform.support.service;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import org.springframework.beans.factory.ListableBeanFactory;
import java.util.*;

/** Explicit bijective identity mapping from service bean metadata; no casing guesses or bean initialization. */
public final class BusinessEntityIdentityResolver {
    private final ListableBeanFactory beans;
    private volatile Map<String,String> mappings;
    public BusinessEntityIdentityResolver(ListableBeanFactory beans) { this.beans=beans; }

    private Map<String,String> mappings() {
        if (mappings != null) return mappings;
        synchronized (this) {
            if (mappings != null) return mappings;
            Map<String,String> values=new LinkedHashMap<>();
            for(String name:beans.getBeanNamesForType(cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor.class,false,false)) {
                var contributor=beans.getBean(name,cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelContributor.class);
                for(var declaration:contributor.declarations()) {
                    if(declaration.nativeEntityType()==null || declaration.nativeEntityType().isBlank()) continue;
                    var model=declaration.descriptor();
                    put(values,model.ownerModule()+"/"+model.entityType(),declaration.nativeEntityType());
                }
            }
            Set<String> serviceIdentities=new HashSet<>();
            for (String name: beans.getBeanNamesForType(AbstractBusinessApplicationService.class, false, false)) {
                var identity=beans.findAnnotationOnBean(name, BusinessEntityService.class, false);
                if (identity == null || identity.nativeEntityType().isBlank()) continue;
                String key=identity.ownerModule()+"/"+identity.entityType();
                String nativeKey=identity.ownerModule()+"/"+identity.nativeEntityType();
                if (!serviceIdentities.add(key))
                    throw new BusinessContractException("IDENTITY_MAPPING_CONFLICT", "业务身份映射必须唯一: "+key);
                put(values,key,identity.nativeEntityType());
            }
            Set<String> nativeIdentities=new HashSet<>();
            for(var mapping:values.entrySet()) {
                String owner=mapping.getKey().substring(0,mapping.getKey().indexOf('/'));
                String nativeKey=owner+"/"+mapping.getValue();
                if(!nativeIdentities.add(nativeKey) || !nativeKey.equals(mapping.getKey()) && values.containsKey(nativeKey))
                    throw new BusinessContractException("IDENTITY_MAPPING_CONFLICT","Ambiguous declared/native identity: "+nativeKey);
            }
            mappings=Map.copyOf(values);
            return mappings;
        }
    }

    private static void put(Map<String,String> values,String key,String nativeType) {
        String prior=values.putIfAbsent(key,nativeType);
        if(prior!=null && !prior.equals(nativeType)) throw new BusinessContractException("IDENTITY_MAPPING_CONFLICT","Conflicting declared/native identity: "+key);
    }

    public EntityRef declaredRef(EntityRef ref) {
        for(var mapping:mappings().entrySet()) {
            String prefix=ref.ownerModule()+"/";
            if(mapping.getKey().startsWith(prefix) && mapping.getValue().equals(ref.entityType()))
                return new EntityRef(ref.tenantId(),ref.ownerModule(),mapping.getKey().substring(prefix.length()),ref.entityId());
        }
        return ref;
    }

    public boolean hasMapping(String owner, String type) { return mappings().containsKey(owner+"/"+type); }
    public EntityRef nativeRef(EntityRef ref) {
        String type=mappings().get(ref.ownerModule()+"/"+ref.entityType());
        return type == null ? ref : new EntityRef(ref.tenantId(),ref.ownerModule(),type,ref.entityId());
    }
    public EntityDataRef nativeRef(EntityDataRef ref) { return new EntityDataRef(nativeRef(ref.entity()),ref.revisionId()); }
    public RevisionRef nativeRef(RevisionRef ref) { return new RevisionRef(nativeRef(ref.entity()),ref.revisionId()); }
}
