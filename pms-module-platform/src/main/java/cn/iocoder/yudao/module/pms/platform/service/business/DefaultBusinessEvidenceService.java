package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.BusinessConditionEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DefaultDeliveryListQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query.EntityValueQuery;
import cn.iocoder.yudao.module.pms.platform.service.entity.EntityProviderRegistry;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntitySaveSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Shared evidence for independent condition evaluation and any project execution backend. */
@Service @RequiredArgsConstructor
public class DefaultBusinessEvidenceService implements BusinessConditionEvidenceApi {
    private final DirectBusinessOwners owners;
    private final EntityCapabilityMapper extensions;
    private final EntityProviderRegistry identities;
    private final DeliveryMaterialMapper materials;
    private final FileEvidenceApi files;
    private final PlatformTransactionManager transactionManager;

    private ProjectBusinessService<?> owner(EntityRef entity,Long projectId,boolean lock){
        var owner=owners.byIdentity(entity.ownerModule(),entity.entityType()).orElseThrow(()->new IllegalArgumentException("BUSINESS_EVIDENCE_OWNER_UNAVAILABLE"));
        owner.requireEvidenceScope(entity,projectId,lock);return owner;
    }
    private <T> T observe(boolean lock,java.util.function.Supplier<T> work){
        if(lock){BusinessEntitySaveSupport.requireTransaction();return work.get();}
        var read=new TransactionTemplate(transactionManager);read.setReadOnly(true);return read.execute(status->work.get());
    }
    @Override public Evidence inspect(Query query){return observe(query.lock(),()->{
        var owner=owner(query.entity(),query.projectId(),query.lock());
        var facts=new TreeMap<String,FactObservation>();var versions=new ArrayList<Object>();
        if(!query.extensionCodes().isEmpty()){
            var target=identities.nativeRef(owner.requireEvidenceScope(query.entity(),query.projectId(),query.lock()));
            var selection=EntityValueQuery.of(target);
            var row=query.lock()?extensions.lockValues(selection):extensions.selectValues(selection);
            var binding=query.lock()?extensions.lockBinding(selection):extensions.selectBinding(selection);
            Long definitionId=row!=null?row.getDefinitionRevisionId():binding==null?null:binding.getExtensionDefinitionRevisionId();
            var definition=definitionId==null?null:extensions.selectDefinition(definitionId);
            if(definition!=null && (!target.entity().tenantId().equals(definition.getTenantId())
                    || !target.entity().ownerModule().equals(definition.getOwnerModule()) || !target.entity().entityType().equals(definition.getEntityType())))
                throw new IllegalStateException("BUSINESS_EXTENSION_IDENTITY_MISMATCH");
            var known=definition==null?Set.<String>of():JsonUtils.parseArray(definition.getFieldsJson(),EntityExtensionApi.Definition.class)
                    .stream().map(EntityExtensionApi.Definition::code).collect(java.util.stream.Collectors.toSet());
            @SuppressWarnings("unchecked") Map<String,Object> values=row==null?Map.of():JsonUtils.parseObject(row.getValuesJson(),Map.class);
            String basis=definitionId+":"+(row==null?0:row.getVersion());versions.add(basis);
            for(String code:query.extensionCodes())if(known.contains(code)){
                Object value=values.get(code);
                facts.put(code,new FactObservation(code,value==null?FactObservation.Availability.EMPTY:FactObservation.Availability.VALUE,value,basis,Completeness.COMPLETE));
            }
        }
        if(!query.deliveryTypes().isEmpty()){
            var delivery=readDeliveries(query.entity(),query.projectId(),query.lock());versions.add(delivery.factVersion());
            for(String type:query.deliveryTypes())facts.put("$delivery:"+type,new FactObservation("$delivery:"+type,
                    FactObservation.Availability.VALUE,Boolean.TRUE.equals(delivery.facts().get(ProjectBusinessRuntimeApi.DELIVERY_PREFIX+type)),delivery.factVersion(),Completeness.COMPLETE));
        }
        return new Evidence(facts,digest(versions));
    });}
    @Override public ProjectBusinessRuntimeApi.DeliveryFacts deliveries(EntityRef entity,Long projectId,boolean lock){
        return observe(lock,()->readDeliveries(entity,projectId,lock));
    }
    private ProjectBusinessRuntimeApi.DeliveryFacts readDeliveries(EntityRef entity,Long projectId,boolean lock){
        var model=owner(entity,projectId,lock).definition();var code=model.stableCode();var query=new DefaultDeliveryListQuery();
        query.setTenantId(entity.tenantId());query.setProjectId(projectId);query.setBusinessType(code);query.setEntityId(entity.entityId());
        query.setReadableBusinessTypes(Set.of(code));query.setPageSize(200);query.setPageNo(1);
        var facts=new TreeMap<String,Boolean>();var versions=new ArrayList<Object>();
        while(true){
            var rows=materials.selectDefaultDeliveryList(query);
            for(var row:rows){
                if(!Objects.equals(row.getTenantId(),entity.tenantId()) || !Objects.equals(row.getProjectId(),projectId)
                        || !Objects.equals(row.getEntityId(),entity.entityId()) || !code.equals(row.getBusinessTypeCode())
                        || !model.ownerModule().equals(row.getOwnerModule()) || !model.entityType().equals(row.getEntityType()))
                    throw new IllegalStateException("BUSINESS_DELIVERY_IDENTITY_MISMATCH");
                String fact=ProjectBusinessRuntimeApi.DELIVERY_PREFIX+row.getTypeCode();
                if(!ProjectBusinessRuntimeApi.deliveryFact(fact))throw new IllegalStateException("BUSINESS_DELIVERY_TYPE_INVALID");
                var document=files.inspectDocument(entity.tenantId(),row.getFileReferenceId());
                boolean available=cn.iocoder.yudao.module.pms.platform.service.businessmodel.DefaultDeliveryEvidence.available(row,document);
                if(available && lock){
                    var checked=files.lockAndRevalidate(new FileEvidenceApi.Query(entity.tenantId(),document.artifactId(),document.versionNo(),document.ownerContext(),document.objectType(),document.objectId(),document.purposeCode(),document.referenceKey(),document.sha256()));
                    available=checked!=null && checked.valid();
                }
                facts.merge(fact,available,(left,right)->left||right);
                versions.add(Arrays.asList(row.getId(),row.getVersion(),row.getFileReferenceId(),available,document==null?null:document.sha256()));
            }
            if(rows.size()<200)break;
            query.setPageNo(Math.incrementExact(query.getPageNo()));
        }
        facts.put(ProjectBusinessRuntimeApi.DELIVERY_COMPLETE,true);
        return new ProjectBusinessRuntimeApi.DeliveryFacts(facts,digest(versions));
    }
    private static String digest(Object value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(value).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
