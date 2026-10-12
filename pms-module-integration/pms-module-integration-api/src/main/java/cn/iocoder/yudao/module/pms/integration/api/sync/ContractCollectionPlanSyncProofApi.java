package cn.iocoder.yudao.module.pms.integration.api.sync;

import java.util.List;
import java.util.Map;

/** Read-only provenance for plans written through the designated sync task. */
public interface ContractCollectionPlanSyncProofApi {
    record Query(Long tenantId, Long actorId, Long taskId, List<String> sourceKeys) {}
    /** originalFields contains all sixteen original column names, including explicit nulls. */
    record Proof(String sourceKey, Long targetId, Map<String,String> originalFields) {}
    List<Proof> verify(Query query);
}
