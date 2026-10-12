package cn.iocoder.yudao.module.pms.commerce.api.migration;

import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** COM-owned historical contract membership and receivable revisions. Never activates current relations or authority. */
public interface DppmsContractHistoryImportApi {
    List<NativeEntityMigrationRow> importHistory(Command command);
    Page page(Query query);
    record Command(Long tenantId,Long actorId,Long projectId,String sourceProjectKey,
                   List<Relation> relations,List<Receivable> receivables) {}
    @lombok.Builder
    record Relation(String sourceKey,String sourceChecksum,Long sourceProjectGroupId,
                    String contractNo,String projectGroupCode,LocalDateTime sourceCreatedAt,String sourceCreatedBy,
                    LocalDateTime sourceUpdatedAt,String sourceUpdatedBy) {}
    /** The original source DOUBLE and nullable source fields are retained independently of canonical projections. */
    @lombok.Builder
    record Receivable(String sourceKey,String sourceChecksum,String contractNo,String sourceBatchCode,
                      String projectName,String sourceOrderNo,String customerCode,String customerName,
                      BigDecimal contractAmount,BigDecimal deliveredAmount,BigDecimal collectedAmount,
                      Double sourceCollectedMoneyRatio,BigDecimal receivableAmount,BigDecimal overdueAmount,
                      String marketName,String departmentName,String industryName,String marketingRepresentativeName,
                      String currencyName,String sourceCreatedBy,LocalDateTime sourceCreatedAt,
                      String sourceUpdatedBy,LocalDateTime sourceUpdatedAt,LocalDateTime sourceEffectiveFrom,
                      LocalDateTime sourceEffectiveTo,String importBatchNo,LocalDateTime contractCreateTime,
                      String projectCode,String marketCode,Integer systemSourceKey,Integer industryCode,
                      String departmentCode,Integer expendSourceKey,String marketingRepresentativeCode,
                      LocalDateTime latestShipTime,String secondaryRepresentativeCode,Integer originalSystemId,
                      Integer originalExpendId,String originalIndustryName,String sourceDataSource) {}
    record Query(Long tenantId,Long actorId,Long projectId,String kind,int pageNo,int pageSize) {}
    record Row(Long id,String sourceKey,Map<String,Object> values) {}
    record Page(List<Row> rows,long total) {}
}
