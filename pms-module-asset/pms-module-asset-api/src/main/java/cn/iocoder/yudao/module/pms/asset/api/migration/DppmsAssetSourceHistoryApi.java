package cn.iocoder.yudao.module.pms.asset.api.migration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Typed software observations and change batches; never commands to upgrade a device. */
public interface DppmsAssetSourceHistoryApi {
    List<NativeEntityMigrationRow> importSourceHistory(Command command);
    /** Read-only validation of the same software batch/version command; no factory imports. */
    record Preview(String domain,String sourceKey,Long targetId) {}
    List<Preview> previewSourceHistory(Command command);
    PageResult<ObservationRecord> pageObservations(Query query);
    PageResult<BatchRecord> pageBatches(Query query);
    PageResult<FactoryRecord> pageFactories(Query query);
    record Command(Long tenantId, Long actorId, Long projectId, String sourceProjectKey,
                   String sourceVersion, LocalDateTime sourceReadAt,
                   List<SoftwareObservation> observations, List<SoftwareBatch> batches, List<FactoryVersion> factories) {
        public Command(Long tenantId, Long actorId, Long projectId, String sourceProjectKey,
                String sourceVersion, LocalDateTime sourceReadAt, List<SoftwareObservation> observations,
                List<SoftwareBatch> batches) {
            this(tenantId,actorId,projectId,sourceProjectKey,sourceVersion,sourceReadAt,observations,batches,List.of());
        }
    }
    record FactoryVersion(String sourceKey, String serialNumber, String conp, String boot, String cpld, String pcb,
                          Long mappedRecordId) {
        public FactoryVersion(String sourceKey,String serialNumber,String conp,String boot,String cpld,String pcb) {
            this(sourceKey,serialNumber,conp,boot,cpld,pcb,null);
        }
        public FactoryVersion(String serialNumber,String conp,String boot,String cpld,String pcb) {
            this(serialNumber,serialNumber,conp,boot,cpld,pcb);
        }
    }
    record FactoryValues(String deviceSn, String conpVersion, String bootVersion, String cpldVersion, String pcbVersion) {}
    record FactoryRecord(Long id, Long projectId, FactoryValues values) {}
    record Query(Long tenantId, Long actorId, Long projectId, String deviceSn, int pageNo, int pageSize) {}
    record SoftwareObservation(Long sourceId,
            Long sourceProjectId,
            Long logId,
            String contractNo,
            String itemCode,
            String barCode,
            String conp,
            String conpType,
            String conpSeries,
            String conpMark,
            String conpBak,
            Integer conpChange,
            String cpld,
            String cpldBak,
            Integer cpldChange,
            String boot,
            String bootBak,
            Integer bootChange,
            String pcb,
            String pcbBak,
            Integer pcbChange,
            LocalDate executeTime,
            Integer datastate,
            LocalDateTime sourceCreatedAt,
            String sourceCreatedBy,
            LocalDateTime sourceUpdatedAt,
            String sourceUpdatedBy,
            String customInfo,
            Long mappedRecordId, Long mappedBatchId, Long extensionDefinitionId) {
        public SoftwareObservation(Long sourceId, Long sourceProjectId, Long logId, String contractNo, String itemCode,
                String barCode, String conp, String conpType, String conpSeries, String conpMark, String conpBak,
                Integer conpChange, String cpld, String cpldBak, Integer cpldChange, String boot, String bootBak,
                Integer bootChange, String pcb, String pcbBak, Integer pcbChange, LocalDate executeTime, Integer datastate,
                LocalDateTime sourceCreatedAt, String sourceCreatedBy, LocalDateTime sourceUpdatedAt,
                String sourceUpdatedBy, String customInfo) {
            this(sourceId,sourceProjectId,logId,contractNo,itemCode,barCode,conp,conpType,conpSeries,conpMark,conpBak,
                    conpChange,cpld,cpldBak,cpldChange,boot,bootBak,bootChange,pcb,pcbBak,pcbChange,executeTime,datastate,
                    sourceCreatedAt,sourceCreatedBy,sourceUpdatedAt,sourceUpdatedBy,customInfo,null,null,null);
        }
        public SoftwareObservation withMapping(Long recordId,Long batchId,Long definitionId) {
            return new SoftwareObservation(sourceId,sourceProjectId,logId,contractNo,itemCode,barCode,conp,conpType,
                    conpSeries,conpMark,conpBak,conpChange,cpld,cpldBak,cpldChange,boot,bootBak,bootChange,pcb,pcbBak,
                    pcbChange,executeTime,datastate,sourceCreatedAt,sourceCreatedBy,sourceUpdatedAt,sourceUpdatedBy,
                    customInfo,recordId,batchId,definitionId);
        }
    }
    record SoftwareBatch(Long sourceId,
            Long sourceProjectId,
            String changeVersion,
            String changeRemark,
            Integer latest,
            String sourceCreatedBy,
            LocalDateTime sourceCreatedAt,
            String sourceUpdatedBy,
            LocalDateTime sourceUpdatedAt,
            Long mappedRecordId) {
        public SoftwareBatch(Long sourceId, Long sourceProjectId, String changeVersion, String changeRemark,
                Integer latest, String sourceCreatedBy, LocalDateTime sourceCreatedAt, String sourceUpdatedBy,
                LocalDateTime sourceUpdatedAt) {
            this(sourceId,sourceProjectId,changeVersion,changeRemark,latest,sourceCreatedBy,sourceCreatedAt,
                    sourceUpdatedBy,sourceUpdatedAt,null);
        }
    }
    record ObservationRecord(Long id, Long projectId, Map<String,Object> values) {}
    record BatchValues(String changeVersion, String changeRemark, Integer latest, String creator,
            LocalDateTime createTime, String updater, LocalDateTime updateTime) {}
    record BatchRecord(Long id, Long projectId, BatchValues values) {}
}
