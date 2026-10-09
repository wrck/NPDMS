package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.runtime.ProjectBusinessRuntimeApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityRef;
import java.util.Map;
import java.util.Set;

/** Internal common evidence, guarded by the owning entity's exact tenant/project identity.
 * Interactive callers must authorize the entity before invoking this port. No HTTP content endpoint.
 */
public interface BusinessConditionEvidenceApi {
    record Query(EntityRef entity, Long projectId, Set<String> extensionCodes, Set<String> deliveryTypes, boolean lock) {
        public Query { extensionCodes=Set.copyOf(extensionCodes); deliveryTypes=Set.copyOf(deliveryTypes); }
    }
    record Evidence(Map<String, FactObservation> facts, String version) {
        public Evidence { facts=Map.copyOf(facts); }
    }
    Evidence inspect(Query query);
    ProjectBusinessRuntimeApi.DeliveryFacts deliveries(EntityRef entity, Long projectId, boolean lock);
}
