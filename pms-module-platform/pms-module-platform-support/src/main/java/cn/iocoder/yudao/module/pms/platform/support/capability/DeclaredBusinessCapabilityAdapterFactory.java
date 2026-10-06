package cn.iocoder.yudao.module.pms.platform.support.capability;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityData;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.support.access.DefaultBusinessEntityAccess;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.*;

/** Declared ordinary entities reuse the capability APIs without an entity-specific Provider. */
@Component
public final class DeclaredBusinessCapabilityAdapterFactory {
    private final BusinessEntityPersistenceRegistry persistence;
    private final ObjectProvider<BusinessEntityAccessPort> access;
    private final ObjectProvider<BusinessOperationDispatcher> operations;
    private final BusinessCallerContext callers;
    private final ObjectProvider<BusinessEntityIdentityResolver> identities;
    public DeclaredBusinessCapabilityAdapterFactory(BusinessEntityPersistenceRegistry persistence,
            ObjectProvider<BusinessEntityAccessPort> access,ObjectProvider<BusinessOperationDispatcher> operations,
            BusinessCallerContext callers,ObjectProvider<BusinessEntityIdentityResolver> identities) {
        this.persistence=persistence;this.access=access;this.operations=operations;this.callers=callers;this.identities=identities;
    }
    private EntityRef declared(EntityRef ref) {
        var resolver=identities.getIfAvailable();return resolver==null?ref:resolver.declaredRef(ref);
    }
    public boolean supports(EntityRef ref) {
        var entity=declared(ref);
        return persistence.find(entity.ownerModule(),entity.entityType()).map(declaration->
                declaration.revisionMapper()==null && declaration.descriptor().scopeBinding()!=null
                && declaration.descriptor().capabilities().stream().anyMatch(cap->cap.enabled() && cap.type()==BusinessCapabilityType.DYNAMIC_FORM)).orElse(false);
    }
    private BusinessModelDeclaration require(EntityRef ref) {
        if(!supports(ref)) throw new BusinessContractException("ENTITY_PROVIDER_UNAVAILABLE","Declared current-object capability is unavailable");
        var entity=declared(ref);return persistence.require(entity.ownerModule(),entity.entityType());
    }
    private BusinessEntityData read(EntityDataRef target,EntityActor actor) {
        var caller=callers.require();
        if(!caller.tenantId().equals(actor.tenantId()) || !caller.userId().equals(actor.userId()))
            throw new BusinessContractException("ACCESS_DENIED","Capability actor must match the trusted caller");
        actor.requireTenant(target.entity());require(target.entity());
        if(target.isRevision()) throw new BusinessContractException("REVISION_UNSUPPORTED","No-history entity has no revision");
        var entity=declared(target.entity());
        var port=access.getIfAvailable();
        if(!(port instanceof DefaultBusinessEntityAccess fixed)) throw new BusinessContractException("ENTITY_PROVIDER_UNAVAILABLE","Fixed business read is unavailable");
        var data=fixed.readFixed(EntityDataRef.current(entity),actor,"capability");
        if(!data.available()) throw new BusinessContractException("ENTITY_NOT_FOUND","Capability object is unavailable");
        return data;
    }
    public EntityFieldProvider fields(EntityRef ref) {
        var declaration=require(ref);var model=declaration.descriptor();
        return new EntityFieldProvider() {
            public String ownerModule(){return ref.ownerModule();}
            public String entityType(){return ref.entityType();}
            public String formUsage(){return model.entityType();}
            public boolean usesValidatedExtensionPatch(){return true;}
            public List<EntityField> fields(){return model.fields().stream().map(field->new EntityField(field.code(),field.type(),field.required())).toList();}
            public Map<String,EntityFieldValue> read(EntityDataRef target,EntityActor actor){
                var data=DeclaredBusinessCapabilityAdapterFactory.this.read(target,actor);
                Map<String,EntityFieldValue> values=new LinkedHashMap<>();
                model.fields().forEach(field->values.put(field.code(),field.readable()?EntityFieldValue.known(data.fieldValues().get(field.code())):EntityFieldValue.unavailable()));
                return Collections.unmodifiableMap(values);
            }
            public Long concurrencyBasis(EntityDataRef target,EntityActor actor){return DeclaredBusinessCapabilityAdapterFactory.this.read(target,actor).concurrencyBasis();}
            public void requireReadable(EntityDataRef target,EntityActor actor){DeclaredBusinessCapabilityAdapterFactory.this.read(target,actor);}
            public void lockForWrite(EntityDataRef target,EntityActor actor,Long version){
                require(target.entity());
                var entity=declared(target.entity());
                operations.getObject().capabilityService(entity.ownerModule(),entity.entityType())
                        .lockCapabilityTarget(new EntityDataRef(entity,target.revisionId()),actor,version);
            }
        };
    }
    public DynamicFormBusinessObjectPolicyProvider formPolicy(DynamicFormProviderKey key) {
        var ref=new EntityRef(1L,key.ownerContext(),key.objectType(),1L);
        var model=require(ref).descriptor();
        return new DynamicFormBusinessObjectPolicyProvider() {
            public DynamicFormProviderKey providerKey(){return key;}
            public DynamicFormPolicyFact inspectRevisionCompatibility(DynamicFormRevisionPolicyQuery query){
                boolean compatible=query.providerKey().equals(key) && model.entityType().equals(query.requiredUsage())
                        && query.fields().stream().allMatch(field->!field.controlledFile() && compatible(field,model));
                return new DynamicFormPolicyFact(query.action(),compatible,compatible?null:"DECLARED_FORM_SCHEMA_INCOMPATIBLE",
                        query.revisionFactVersion().longValue(),"DECLARED_ENTITY_FIELDS");
            }
            public DynamicFormPolicyFact inspectInstanceOwnerPolicy(DynamicFormInstancePolicyQuery query){
                return new DynamicFormPolicyFact(query.action(),false,"ENTITY_FORM_HAS_NO_INSTANCE",null,"ENTITY_ONLY");
            }
            public DynamicFormPolicyFact lockAndRevalidateInstanceOwnerPolicy(DynamicFormPolicyRevalidationQuery query){
                return new DynamicFormPolicyFact(query.expectedFact().action(),false,"ENTITY_FORM_HAS_NO_INSTANCE",null,"ENTITY_ONLY");
            }
        };
    }
    private boolean compatible(DynamicFormFieldDescriptor form,BusinessModelDescriptor model) {
        var fixed=model.fields().stream().filter(field->field.code().equals(form.fieldKey())).findFirst();
        if(fixed.isEmpty()) return Set.of("any","number","boolean","array").contains(form.valueType());
        return switch(fixed.get().type()) {
            case NUMBER -> "number".equals(form.valueType());
            case BOOLEAN -> "boolean".equals(form.valueType());
            case TEXT_LIST,OBJECT_LIST -> "array".equals(form.valueType());
            default -> "any".equals(form.valueType());
        };
    }
}
