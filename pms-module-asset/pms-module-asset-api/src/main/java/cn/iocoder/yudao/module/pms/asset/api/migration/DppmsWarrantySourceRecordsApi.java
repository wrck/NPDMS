package cn.iocoder.yudao.module.pms.asset.api.migration;

import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Original service, registration and warranty change facts; never commands to activate a renewal. */
public interface DppmsWarrantySourceRecordsApi {
    List<NativeEntityMigrationRow> importRecords(Command command);
    Page page(Query query);
    record Command(Long tenantId, Long actorId, Long projectId, String sourceProjectKey,
                   String sourceVersion, LocalDateTime sourceReadAt,
                   List<ServiceRecord> services, List<Registration> registrations, List<Change> changes) {}
    record Query(Long tenantId, Long actorId, Long projectId, String kind, int pageNo, int pageSize) {}
    record Row(Long id, String sourceKey, Map<String, Object> values) {}
    record Page(List<Row> list, Long total) {}
    /** sourceKey is the captured multiset identity; the nullable/nonunique original id stays sourceId. */
    record ServiceRecord(String sourceKey, String sourceId, String renewalContractNo, String deviceSn,
                         String grade, LocalDateTime beginDate, LocalDateTime endDate, String warrantyFlag,
                         String remark, Integer extensionFlag, Integer sourceState) {}
    record Registration(Long sourceId, Integer packageSyncId, Integer contractSyncId, String deviceSn,
                        String officeCode, String sourceProjectName, String contractNo, Integer contractType,
                        String customerName, String itemCode, String itemName, String warrantyLevel,
                        LocalDateTime warrantyStartTime, LocalDateTime warrantyEndTime, Integer warrantyLimit) {}
    record Change(Long sourceId, String sourceUsername, Integer updateType, String deviceSn,
                  LocalDateTime warrantyStartTime, LocalDateTime warrantyEndTime, Integer warrantyTimes,
                  LocalDateTime sourceUpdatedAt) {}
}
