package cn.iocoder.yudao.module.pms.asset.api.migration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** One-time historical licenses. No plaintext code crosses this Owner contract. */
public interface DppmsLicenseRecordApi {
    List<Preview> preview(Command command);
    List<Target> apply(Command command);
    PageResult<Row> page(Query query);
    record Envelope(String sourceKey, String ciphertext, String keyId, String codeMac) {}
    record License(String sourceKey, String deviceSn, String productModel, String contractNo,
                   String contractType, String productCode, String licenseStatus, Long contractId, Envelope envelope) {}
    record Command(Long tenantId, Long actorId, Long projectId, Long snapshotId,
                   LocalDateTime extractedAt, List<License> licenses) {}
    record Preview(String sourceKey, Long targetId, String action, List<String> pendingAssociations) {}
    record Target(String sourceKey, Long id) {}
    /** IDs originate exclusively from the authorized project's successful migration bindings. */
    record Query(Long tenantId, Long actorId, Long projectId, List<Long> recordIds, int pageNo, int pageSize) {}
    record Row(Long id, Map<String,Object> values) {}
}
