package cn.iocoder.yudao.module.pms.engineering.api.installation;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import java.time.LocalDateTime;

/** Queries adjudicated historical installations from the existing formal installation entity. */
public interface DppmsInstallationSourceQueryApi {
    PageResult<SourceRecord> pageSourceRecords(Query query);
    record Query(Long tenantId,Long actorId,Long projectId,Long equipmentId,int pageNo,int pageSize) {}
    record SourceRecord(Long id,Long projectId,Long equipmentId,String sourceRecordKey,
            String sourceDeviceSn,String sourceProjectKey,Long sourceId,Long sourceProjectId,String installLocation,
            Long addressId,Long addressVersion,String locationResolutionStatus,
            DppmsInstallationAddressImportApi.Details values,HistoricalInstallation historicalInstallation) {
        public SourceRecord(Long id,Long projectId,Long equipmentId,String sourceRecordKey,
                String sourceDeviceSn,String sourceProjectKey,Long sourceId,Long sourceProjectId,String installLocation,
                Long addressId,Long addressVersion,String locationResolutionStatus,
                DppmsInstallationAddressImportApi.Details values) {
            this(id,projectId,equipmentId,sourceRecordKey,sourceDeviceSn,sourceProjectKey,sourceId,sourceProjectId,
                    installLocation,addressId,addressVersion,locationResolutionStatus,values,null);
        }
    }
    /** Read-only canonical history; status and current pointer are reported without activating any workflow. */
    record HistoricalInstallation(Long id,Long projectId,Long equipmentId,String code,String sourceRecordKey,
            String sourceDeviceSn,String sourceProjectKey,Boolean sourceHistory,Integer status,String installLocation,
            Long addressId,Long addressVersion,String locationResolutionStatus,Long currentEquipmentId,
            LocalDateTime effectiveFrom,LocalDateTime effectiveTo,Long version) {}
}
