package cn.iocoder.yudao.module.pms.commerce.api.migration;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import java.util.*;
public interface DppmsCommerceDetailImportApi {
    List<NativeEntityMigrationRow> importDetails(Command command);
    Page page(Query query);
    record Command(DppmsCommerceEntityResolveApi.Command identities,List<Order> orders,List<Line> lines,
                   List<Package> packages,List<Plan> plans,List<Execution> executions) {
        public Command(DppmsCommerceEntityResolveApi.Command identities,List<Order> orders,List<Line> lines,
                       List<Package> packages,List<Plan> plans) {
            this(identities,orders,lines,packages,plans,List.of());
        }
    }
    /** Canonical properties follow existing sync transformations. */
    record Execution(String sourceKey,String executionNo,String sourceSystem,String projectCode,String projectName,
                     String salesRepCode,String salesRepName,String marketCode,String marketName,String systemSourceKey,
                     String systemName,String expendSourceKey,String expendName,String industryCode,String industryName,
                     String departmentCode,String departmentName,String serviceTypeName,String channelName,String engineeringFeeRaw,
                     String sourceObjectId,String applyType,String sourceCompanyCode,String customerProjectName,
                     String finalCustomerName,String agentName,String majorProjectLevel,java.math.BigDecimal projectAmount,
                     java.time.LocalDateTime submitTime,java.time.LocalDateTime predictedBidTime,String contactName,String contactPhone) {}
    record Query(Long tenantId,Long actorId,Long projectId,String kind,int pageNo,int pageSize) {}
    record Row(Long id,String sourceKey,Map<String,Object> values) {}
    record Page(List<Row> list,Long total) {}
    record Order(String sourceKey, String contractNo, String executionNo, java.time.LocalDateTime orderCreateTime, java.time.LocalDateTime customerRequiredTime, String customerCode, String customerName, String sourceProjectName, String orderComment, String salesType, String customInfo, String sourceCompanyCode, String sourceErpSystem, String legacyRecordId, java.time.LocalDateTime originalSourceSyncTime) {
        public Order(String sourceKey, String contractNo, String executionNo, java.time.LocalDateTime orderCreateTime, java.time.LocalDateTime customerRequiredTime, String customerCode, String customerName, String sourceProjectName, String orderComment, String salesType, String customInfo, String sourceCompanyCode, String sourceErpSystem, String legacyRecordId) {
            this(sourceKey, contractNo, executionNo, orderCreateTime, customerRequiredTime, customerCode, customerName, sourceProjectName, orderComment, salesType, customInfo, sourceCompanyCode, sourceErpSystem, legacyRecordId, null);
        }
    }
    record Line(String sourceKey, String productCode, String productDesc, java.math.BigDecimal orderQty, java.math.BigDecimal openQty, String bundleCode, Integer warrantyMonth, String lineType, String profitCenter, String realExecutionNo, String customInfo, String sourceCompanyCode, String sourceErpSystem, String legacyRecordId, java.time.LocalDateTime originalSourceSyncTime) {
        public Line(String sourceKey, String productCode, String productDesc, java.math.BigDecimal orderQty, java.math.BigDecimal openQty, String bundleCode, Integer warrantyMonth, String lineType, String profitCenter, String realExecutionNo, String customInfo, String sourceCompanyCode, String sourceErpSystem, String legacyRecordId) {
            this(sourceKey, productCode, productDesc, orderQty, openQty, bundleCode, warrantyMonth, lineType, profitCenter, realExecutionNo, customInfo, sourceCompanyCode, sourceErpSystem, legacyRecordId, null);
        }
    }
    record Package(String sourceKey, String packageNo, java.time.LocalDateTime shipmentTime, java.time.LocalDateTime warrantyStartTime, java.time.LocalDateTime warrantyEndTime, String receiverName, String expressNo, String carrierName, String sourceContractKey, String carrierCode) {}
    record Plan(String sourceKey, String contractNo, String batchCode, String planName, String referenceEventName, java.time.LocalDateTime eventPlannedAt, java.math.BigDecimal afterDays, java.time.LocalDateTime eventActualFinishedAt, String marketingFeedback, String dataSource, java.time.LocalDateTime sourceCreatedAt, String sourceCreatedBy, java.time.LocalDateTime sourceUpdatedAt, String sourceUpdatedBy, java.time.LocalDateTime effectiveFrom, java.time.LocalDateTime effectiveTo) {}
}
