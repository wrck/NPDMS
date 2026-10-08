package cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Internal read-only project integration. No runtime command may bypass the business Controller/Service. */
public interface ProjectBusinessRuntimeApi {
    String DELIVERY_PREFIX="BUSINESS_DELIVERY_UPLOADED:";
    String DELIVERY_COMPLETE="BUSINESS_DELIVERY_OBSERVATION_COMPLETE";
    static boolean deliveryFact(String code){return code!=null && code.startsWith(DELIVERY_PREFIX)
            && code.substring(DELIVERY_PREFIX.length()).matches("[A-Za-z0-9_.:-]{1,64}");}
    record Type(String ownerModule,String entityType) { }
    record Definition(Type type,String businessType,String title,Map<String,String> factLabels,String nativeObjectType) {
        public Definition { factLabels=Map.copyOf(factLabels); }
    }
    record Query(Long tenantId,Long projectId,Type type,Long entityId) { }
    record Candidates(Long tenantId,Long projectId,Type type,Long afterId,int limit) { }
    record Reference(Long entityId,String factVersion) { }
    record Observation(Long entityId,String factVersion,Map<String,Boolean> facts,boolean handlingCompleted,LocalDateTime formedAt) {
        public Observation { facts=Map.copyOf(facts); }
    }
    record UserContext(Long tenantId,Long userId,Long projectId,Type type) { }
    record DeliveryFacts(Map<String,Boolean> facts,String factVersion) {
        public DeliveryFacts { facts=Map.copyOf(facts); }
    }
    List<Definition> definitions();
    DeliveryFacts deliveryFacts(Query query,boolean lock);
    Observation inspect(Query query);
    /** Caller transaction, exact tenant/project and locked business identity; not an interactive user read. */
    Observation lockAndInspect(Query query);
    List<Reference> candidates(Candidates query);
    Set<String> actions(UserContext context);
    Observation inspectForUser(UserContext context,Long entityId,boolean lock,String expectedFactVersion);
}
