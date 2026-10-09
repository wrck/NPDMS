package cn.iocoder.yudao.module.pms.platform.service.business;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.module.pms.platform.support.business.ProjectBusinessService;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessEntitySaveSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** One internal projection for every inherited Service. It exposes no business body and executes no business command. */
@Service @RequiredArgsConstructor
public class DirectBusinessRuntimeService implements ProjectBusinessRuntimeApi {
    private final DirectBusinessOwners owners;
    private final cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution.BusinessConditionEvidenceApi evidence;
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
        var owner=service(request.type());
        return evidence.deliveries(new cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef(request.tenantId(),
                owner.definition().ownerModule(),owner.definition().entityType(),request.entityId()),request.projectId(),lock);
    }
}
