package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.DeliveryMaterialMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.delivery.query.DefaultDeliveryListQuery;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntitySaveSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** One internal projection for every inherited Service. It exposes no business body and executes no business command. */
@Service @RequiredArgsConstructor
public class DirectBusinessRuntimeService implements ProjectBusinessRuntimeApi {
    private final DirectBusinessOwners owners;
    private final DeliveryMaterialMapper materials;
    private final FileEvidenceApi files;
    @Override public List<Definition> definitions(){return owners.definitions().stream().map(model->owners.byCode(model.stableCode()).orElseThrow().runtimeDefinition()).toList();}
    private ProjectBusinessService<?> service(Type type){
        var exact=owners.byIdentity(type.ownerModule(),type.entityType());if(exact.isPresent())return exact.get();
        var matches=definitions().stream().filter(def->def.type().ownerModule().equals(type.ownerModule()) && Objects.equals(def.nativeObjectType(),type.entityType())).toList();
        if(matches.size()!=1)throw new IllegalArgumentException("BUSINESS_RUNTIME_OWNER_UNAVAILABLE");
        return owners.byCode(matches.getFirst().businessType()).orElseThrow();
    }
    private Query canonical(Query query,ProjectBusinessService<?> service){return new Query(query.tenantId(),query.projectId(),service.runtimeDefinition().type(),query.entityId());}
    private UserContext canonical(UserContext context,ProjectBusinessService<?> service){return new UserContext(context.tenantId(),context.userId(),context.projectId(),service.runtimeDefinition().type());}
    @Override public Observation inspect(Query query){var service=service(query.type());return service.runtimeObservation(canonical(query,service),false);}
    @Override public Observation lockAndInspect(Query query){BusinessEntitySaveSupport.requireTransaction();var service=service(query.type());return service.runtimeObservation(canonical(query,service),true);}
    @Override public List<Reference> candidates(Candidates query){var service=service(query.type());return service.runtimeCandidates(new Candidates(query.tenantId(),query.projectId(),service.runtimeDefinition().type(),query.afterId(),query.limit()));}
    @Override public Set<String> actions(UserContext context){var service=service(context.type());return service.runtimeActions(canonical(context,service));}
    @Override public Observation inspectForUser(UserContext context,Long id,boolean lock,String expectedVersion){var service=service(context.type());return service.runtimeForUser(canonical(context,service),id,lock,expectedVersion);}
    @Override public DeliveryFacts deliveryFacts(Query request,boolean lock){
        if(lock)BusinessEntitySaveSupport.requireTransaction();
        var service=service(request.type());service.runtimeObservation(canonical(request,service),lock);
        var model=service.definition();var code=model.stableCode();var query=new DefaultDeliveryListQuery();
        query.setTenantId(request.tenantId());query.setProjectId(request.projectId());query.setBusinessType(code);query.setEntityId(request.entityId());
        query.setReadableBusinessTypes(Set.of(code));query.setPageSize(200);query.setPageNo(1);
        var facts=new TreeMap<String,Boolean>();var versions=new ArrayList<Object>();
        while(true){
            var rows=materials.selectDefaultDeliveryList(query);
            for(var row:rows){
                if(!Objects.equals(row.getTenantId(),request.tenantId()) || !Objects.equals(row.getProjectId(),request.projectId())
                        || !Objects.equals(row.getEntityId(),request.entityId()) || !code.equals(row.getBusinessTypeCode())
                        || !model.ownerModule().equals(row.getOwnerModule()) || !model.entityType().equals(row.getEntityType()))
                    throw new IllegalStateException("BUSINESS_DELIVERY_IDENTITY_MISMATCH");
                String fact=DELIVERY_PREFIX+row.getTypeCode();if(!ProjectBusinessRuntimeApi.deliveryFact(fact))throw new IllegalStateException("BUSINESS_DELIVERY_TYPE_INVALID");
                var document=files.inspectDocument(request.tenantId(),row.getFileReferenceId());boolean available=cn.iocoder.yudao.module.pms.platform.service.businessmodel.DefaultDeliveryEvidence.available(row,document);
                if(available && lock){var checked=files.lockAndRevalidate(new FileEvidenceApi.Query(request.tenantId(),document.artifactId(),document.versionNo(),document.ownerContext(),document.objectType(),document.objectId(),document.purposeCode(),document.referenceKey(),document.sha256()));available=checked!=null && checked.valid();}
                facts.merge(fact,available,(left,right)->left||right);
                versions.add(Arrays.asList(row.getId(),row.getVersion(),row.getFileReferenceId(),available,document==null?null:document.sha256()));
            }
            if(rows.size()<200)break;
            query.setPageNo(Math.incrementExact(query.getPageNo()));
        }
        facts.put(DELIVERY_COMPLETE,true);
        try{return new DeliveryFacts(facts,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(versions).getBytes(StandardCharsets.UTF_8))));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
