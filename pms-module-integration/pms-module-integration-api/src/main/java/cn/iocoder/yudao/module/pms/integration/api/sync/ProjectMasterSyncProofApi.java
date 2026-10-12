package cn.iocoder.yudao.module.pms.integration.api.sync;

import java.util.List;
import java.util.Map;

/** Read-only proof that all original header fields were carried through the designated sync Owner. */
public interface ProjectMasterSyncProofApi {
    record Query(Long tenantId, Long actorId, List<String> sourceKeys) {}
    record Proof(String sourceKey, Long targetId, Map<String,String> originalFields) {}
    List<Proof> verify(Query query);
}
