package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import cn.iocoder.yudao.module.pms.integration.api.basesync.SyncWriteAction;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;
import org.apache.ibatis.session.ResultHandler;

/** All 39 original columns; reference fill before each batch, then explicit INSERT/UPDATE. */
@Component
@RequiredArgsConstructor
public class P24ReceivableSyncTask extends AbstractSyncTask<P24ReceivableSyncTask.ReceivableSyncSource,Void> {
    private final TargetMapper receipts;
    @Override public String taskCode(){return "BASE_P24";}
    @Override public String taskName(){return "合同回款信息";}
    @Override public Set<String> requiredPermissions(){return Set.of("pms:commerce:contract:query");}

    @Override public void work(SyncContext<Void> context) { syncData(context.forData("Receivable",false,true)); }
    @Override protected void syncDataQueryByRowHandler(SyncContext<Void> context, ResultHandler<ReceivableSyncSource> handler) {
        long[] previous={0};
        super.syncDataQueryByRowHandler(context,result->{
                    var row=result.getResultObject();
                    if(row.getSourceId()==null || row.getSourceId()<=previous[0])
                        throw new IllegalArgumentException("原回款记录ID缺失或重复");
                    previous[0]=row.getSourceId();
                    handler.handleResult(result);
                });
    }
    @Override protected void syncDataInsertBefore(SyncBatch<ReceivableSyncSource,Void> batch){
        var context=batch.context();
        var eligible=batch.rows().stream().filter(row->row.getContractNo()!=null).toList();
        var sourceIds=eligible.stream().map(row->Objects.toString(row.getSourceId())).toList();
        var bindings=context.identities().findByKeys(new SyncIdentityStore.Query("RECEIVABLE",sourceIds));
        var keys=eligible.stream().map(row->new TargetMapper.SourceKey(row.getSourceSystem(),Objects.toString(row.getSourceId()))).toList();
        var existing=new HashMap<TargetMapper.SourceKey,ReceivableSyncRow>();
        // Bound the OR-expression depth as well as the row count: production SQL interceptors serialize its AST.
        for(int offset=0;offset<keys.size();offset+=100){
            var selected=keys.subList(offset,Math.min(offset+100,keys.size()));
            for(var old:receipts.selectExisting(new TargetMapper.ExistingQuery(context.tenantId(),selected,context.preview()))){
                var key=new TargetMapper.SourceKey(old.getSourceSystem(),old.getSourceRecordKey());
                if(old.getTenantId()!=context.tenantId() || !selected.contains(key) || existing.putIfAbsent(key,old)!=null)
                    throw new IllegalStateException("回款正式来源身份不唯一或与原文不一致");
            }
        }
        for(var source:batch.rows()){
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            if(source.getContractNo()==null)continue;
            if(source.getSourceId()==null || source.getSourceId()<=0) throw new IllegalArgumentException("回款原ID无效");
            var row=convert(source);
            row.setTenantId(context.tenantId());row.setDeleted(false);
            row.setCreator(source.getResolvedCreator());
            row.setUpdater(source.getResolvedUpdater());
            var key=new TargetMapper.SourceKey(row.getSourceSystem(),row.getSourceRecordKey());
            var old=existing.get(key);var binding=bindings.get(row.getSourceRecordKey());
            if(old!=null && (Boolean.TRUE.equals(old.getDeleted()) || binding==null || binding.targetId()!=old.getId()
                    || !Objects.equals(binding.fingerprint(),fingerprint(old))))
                throw new IllegalStateException("回款正式记录或来源基线已变化，禁止接管或覆盖");
            if(old==null && binding!=null) throw new IllegalStateException("回款来源绑定目标缺失");
            if(old==null){
                row.setId(context.preview()?null:IdWorker.getId());
                row.setContractId(null); // Optional association is deferred; preserve the original contract number.
                row.setMappingStatus("PENDING_COMPANY");
            }else{
                row.setId(old.getId());row.setContractId(old.getContractId());row.setCompanyId(old.getCompanyId());
                row.setCompanyCode(old.getCompanyCode());row.setCompanyName(old.getCompanyName());
                row.setCompanyResolutionSource(old.getCompanyResolutionSource());row.setMappingStatus(old.getMappingStatus());
                row.setDepartmentId(old.getDepartmentId());row.setSystemCode(old.getSystemCode());row.setSystemName(old.getSystemName());
                row.setExpendCode(old.getExpendCode());row.setExpendName(old.getExpendName());
            }
            validate(row);
            source.setTarget(row);source.setPreviousFingerprint(binding==null?null:binding.fingerprint());
            if(old!=null && values(old).equals(values(row)))continue;
            source.setSyncAction(old==null?SyncWriteAction.INSERT:SyncWriteAction.UPDATE);
        }
    }
    @Override protected SyncCounts syncDataCount(SyncBatch<ReceivableSyncSource,Void> batch) {
        return SyncCounts.fromActions(batch.rows(),ReceivableSyncSource::getSyncAction);
    }
    @Override protected void syncDataInsert(SyncBatch<ReceivableSyncSource,Void> batch) {
        super.syncDataInsert(batch.matching(row->row.getSyncAction()==SyncWriteAction.INSERT));
        super.syncDataUpdate(batch.matching(row->row.getSyncAction()==SyncWriteAction.UPDATE));
    }
    @Override protected void syncDataInsertAfter(SyncBatch<ReceivableSyncSource,Void> batch) {
        batch.context().identities().saveAll(batch.rows().stream()
                .filter(row->row.getSyncAction()!=SyncWriteAction.UNCHANGED).map(this::identity).toList());
    }
    private SyncIdentityStore.Save identity(ReceivableSyncSource source) {
        var row=source.getTarget();
        return new SyncIdentityStore.Save("RECEIVABLE",row.getSourceRecordKey(),row.getId(),fingerprint(row),source.getPreviousFingerprint());
    }


    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OriginalDivisions(Integer systemSourceKey,Integer expendSourceKey,String industryName){}
    static ReceivableSyncRow convert(ReceivableSyncSource source){
        return ReceivableSyncRow.builder()
                .contractNo(source.getContractNo())
                .projectName(source.getProjectName())
                .sourceOrderNo(source.getSourceOrderNo())
                .customerCode(source.getCustomerCode())
                .customerName(source.getCustomerName())
                .contractAmount(source.getContractAmount())
                .deliveredAmount(source.getDeliveredAmount())
                .collectedAmount(source.getCollectedAmount())
                .collectedRatio(source.getCollectedRatio())
                .receivableAmount(source.getReceivableAmount())
                .overdueAmount(source.getOverdueAmount())
                .currencyName(source.getCurrencyName())
                .marketCode(source.getMarketCode())
                .marketName(source.getMarketName())
                .departmentCode(source.getDepartmentCode())
                .departmentName(source.getDepartmentName())
                .industryName(source.getIndustryName())
                .marketingRepresentativeName(source.getMarketingRepresentativeName())
                .sourceBatchCode(source.getSourceBatchCode())
                .importBatchNo(source.getImportBatchNo())
                .projectCode(source.getProjectCode())
                .systemSourceKey(source.getSystemSourceKey())
                .industryCode(source.getIndustryCode())
                .expendSourceKey(source.getExpendSourceKey())
                .marketingRepresentativeCode(source.getMarketingRepresentativeCode())
                .secondaryRepresentativeCode(source.getSecondaryRepresentativeCode())
                .sourceEffectiveFrom(source.getSourceEffectiveFrom())
                .sourceEffectiveTo(source.getSourceEffectiveTo())
                .contractCreateTime(source.getContractCreateTime())
                .latestShipTime(source.getLatestShipTime())
                .sourceSystem(source.getSourceSystem())
                .createTime(source.getCreateTime())
                .sourceRecordKey(Objects.toString(source.getSourceId()))
                .sourceSyncTime(source.getUpdateTime())
                .updateTime(source.getUpdateTime()==null?source.getCreateTime():source.getUpdateTime())
                .originalDivisionValues(JsonUtils.toJsonString(new OriginalDivisions(source.getOriginalSystemSourceKey(),source.getOriginalExpendSourceKey(),source.getOriginalIndustryName())))
                .build();
    }
    static void validate(ReceivableSyncRow row){
        checkTextLength(row.getContractNo(),64);
        if(row.getContractNo()==null) throw new IllegalArgumentException("回款必填字段缺失：contract_no");
        checkTextLength(row.getCompanyCode(),64);
        checkTextLength(row.getCompanyName(),255);
        checkTextLength(row.getCompanyResolutionSource(),32);
        checkTextLength(row.getMappingStatus(),32);
        if(row.getMappingStatus()==null) throw new IllegalArgumentException("回款必填字段缺失：mapping_status");
        checkTextLength(row.getProjectName(),512);
        checkTextLength(row.getSourceOrderNo(),64);
        checkTextLength(row.getCustomerCode(),64);
        checkTextLength(row.getCustomerName(),512);
        exactDecimal(row.getContractAmount(),20,2);
        if(row.getContractAmount()==null) throw new IllegalArgumentException("回款必填字段缺失：contract_amount");
        exactDecimal(row.getDeliveredAmount(),20,2);
        if(row.getDeliveredAmount()==null) throw new IllegalArgumentException("回款必填字段缺失：delivered_amount");
        exactDecimal(row.getCollectedAmount(),20,2);
        if(row.getCollectedAmount()==null) throw new IllegalArgumentException("回款必填字段缺失：collected_amount");
        exactDecimal(row.getCollectedRatio(),18,6);
        exactDecimal(row.getReceivableAmount(),20,2);
        exactDecimal(row.getOverdueAmount(),20,2);
        checkTextLength(row.getCurrencyName(),32);
        checkTextLength(row.getMarketCode(),64);
        checkTextLength(row.getMarketName(),128);
        checkTextLength(row.getDepartmentCode(),64);
        checkTextLength(row.getDepartmentName(),128);
        checkTextLength(row.getIndustryName(),128);
        checkTextLength(row.getMarketingRepresentativeName(),128);
        checkTextLength(row.getSourceBatchCode(),32);
        checkTextLength(row.getImportBatchNo(),64);
        checkTextLength(row.getProjectCode(),80);
        checkTextLength(row.getSystemSourceKey(),64);
        checkTextLength(row.getSystemCode(),64);
        checkTextLength(row.getSystemName(),255);
        checkTextLength(row.getIndustryCode(),64);
        checkTextLength(row.getExpendSourceKey(),64);
        checkTextLength(row.getExpendCode(),64);
        checkTextLength(row.getExpendName(),255);
        checkTextLength(row.getMarketingRepresentativeCode(),64);
        checkTextLength(row.getSecondaryRepresentativeCode(),64);
        checkMillisecond(row.getSourceEffectiveFrom());
        checkMillisecond(row.getSourceEffectiveTo());
        checkMillisecond(row.getContractCreateTime());
        checkMillisecond(row.getLatestShipTime());
        checkTextLength(row.getSourceSystem(),32);
        if(row.getSourceSystem()==null) throw new IllegalArgumentException("回款必填字段缺失：source_system");
        checkTextLength(row.getSourceRecordKey(),128);
        if(row.getSourceRecordKey()==null) throw new IllegalArgumentException("回款必填字段缺失：source_record_key");
        checkMillisecond(row.getSourceSyncTime());
        checkTextLength(row.getCreator(),64);
        if(row.getCreator()==null) throw new IllegalArgumentException("回款必填字段缺失：creator");
        checkMillisecond(row.getCreateTime());
        if(row.getCreateTime()==null) throw new IllegalArgumentException("回款必填字段缺失：create_time");
        checkTextLength(row.getUpdater(),64);
        if(row.getUpdater()==null) throw new IllegalArgumentException("回款必填字段缺失：updater");
        checkMillisecond(row.getUpdateTime());
        if(row.getUpdateTime()==null) throw new IllegalArgumentException("回款必填字段缺失：update_time");
        if(row.getSourceSystem().isBlank())throw new IllegalArgumentException("回款来源类别为空");
    }
    static List<Object> values(ReceivableSyncRow row){
        var values=new ArrayList<>(Arrays.<Object>asList(
                row.getId(),
                row.getTenantId(),
                row.getContractId(),
                row.getContractNo(),
                row.getCompanyId(),
                row.getCompanyCode(),
                row.getCompanyName(),
                row.getCompanyResolutionSource(),
                row.getMappingStatus(),
                row.getProjectName(),
                row.getSourceOrderNo(),
                row.getCustomerCode(),
                row.getCustomerName(),
                row.getContractAmount(),
                row.getDeliveredAmount(),
                row.getCollectedAmount(),
                row.getCollectedRatio(),
                row.getReceivableAmount(),
                row.getOverdueAmount(),
                row.getCurrencyName(),
                row.getMarketCode(),
                row.getMarketName(),
                row.getDepartmentId(),
                row.getDepartmentCode(),
                row.getDepartmentName(),
                row.getIndustryName(),
                row.getMarketingRepresentativeName(),
                row.getSourceBatchCode(),
                row.getImportBatchNo(),
                row.getProjectCode(),
                row.getSystemSourceKey(),
                row.getSystemCode(),
                row.getSystemName(),
                row.getIndustryCode(),
                row.getExpendSourceKey(),
                row.getExpendCode(),
                row.getExpendName(),
                row.getMarketingRepresentativeCode(),
                row.getSecondaryRepresentativeCode(),
                canonicalDivision(row.getOriginalDivisionValues()),
                row.getSourceEffectiveFrom(),
                row.getSourceEffectiveTo(),
                row.getContractCreateTime(),
                row.getLatestShipTime(),
                row.getSourceSystem(),
                row.getSourceRecordKey(),
                row.getSourceSyncTime(),
                row.getCreator(),
                row.getCreateTime(),
                row.getUpdater(),
                row.getUpdateTime(),
                row.getDeleted()));
        values.replaceAll(value->value instanceof BigDecimal number?number.stripTrailingZeros().toPlainString():value);
        return values;
    }
    static String fingerprint(ReceivableSyncRow row){return SyncFingerprint.of(values(row));}
    private static String canonicalDivision(String json){return json==null?null:JsonUtils.toJsonString(JsonUtils.parseObject(json,OriginalDivisions.class));}

    @lombok.Data
    public static class ReceivableSyncSource implements  SyncReferences.SourceAudit {
        private String resolvedCreator;
        private String resolvedUpdater;
        @Override public boolean requiresAuditReferences() { return contractNo!=null; }
        @Override public boolean usesAuditCatalogue() { return true; }
        private Long sourceId;
        private String contractNo;
        private String sourceBatchCode;
        private String projectName;
        private String sourceOrderNo;
        private String customerCode;
        private String customerName;
        private java.math.BigDecimal contractAmount;
        private java.math.BigDecimal deliveredAmount;
        private java.math.BigDecimal collectedAmount;
        private java.math.BigDecimal collectedRatio;
        private java.math.BigDecimal receivableAmount;
        private java.math.BigDecimal overdueAmount;
        private String marketName;
        private String departmentName;
        private String industryName;
        private String marketingRepresentativeName;
        private String currencyName;
        private String sourceCreator;
        private java.time.LocalDateTime createTime;
        private String sourceUpdater;
        private java.time.LocalDateTime updateTime;
        private java.time.LocalDateTime sourceEffectiveFrom;
        private java.time.LocalDateTime sourceEffectiveTo;
        private String importBatchNo;
        private java.time.LocalDateTime contractCreateTime;
        private String projectCode;
        private String marketCode;
        private String systemSourceKey;
        private String industryCode;
        private String departmentCode;
        private String expendSourceKey;
        private String marketingRepresentativeCode;
        private java.time.LocalDateTime latestShipTime;
        private String secondaryRepresentativeCode;
        private Integer originalSystemSourceKey;
        private Integer originalExpendSourceKey;
        private String originalIndustryName;
        private String sourceSystem;
        private ReceivableSyncRow target;
        private String previousFingerprint;
        private SyncWriteAction syncAction=SyncWriteAction.UNCHANGED;
    }

    @lombok.Data
    @lombok.Builder(toBuilder=true)
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReceivableSyncRow {
        private Long id;
        private Long tenantId;
        private Long contractId;
        private String contractNo;
        private Long companyId;
        private String companyCode;
        private String companyName;
        private String companyResolutionSource;
        private String mappingStatus;
        private String projectName;
        private String sourceOrderNo;
        private String customerCode;
        private String customerName;
        private java.math.BigDecimal contractAmount;
        private java.math.BigDecimal deliveredAmount;
        private java.math.BigDecimal collectedAmount;
        private java.math.BigDecimal collectedRatio;
        private java.math.BigDecimal receivableAmount;
        private java.math.BigDecimal overdueAmount;
        private String currencyName;
        private String marketCode;
        private String marketName;
        private Long departmentId;
        private String departmentCode;
        private String departmentName;
        private String industryName;
        private String marketingRepresentativeName;
        private String sourceBatchCode;
        private String importBatchNo;
        private String projectCode;
        private String systemSourceKey;
        private String systemCode;
        private String systemName;
        private String industryCode;
        private String expendSourceKey;
        private String expendCode;
        private String expendName;
        private String marketingRepresentativeCode;
        private String secondaryRepresentativeCode;
        private String originalDivisionValues;
        private java.time.LocalDateTime sourceEffectiveFrom;
        private java.time.LocalDateTime sourceEffectiveTo;
        private java.time.LocalDateTime contractCreateTime;
        private java.time.LocalDateTime latestShipTime;
        private String sourceSystem;
        private String sourceRecordKey;
        private java.time.LocalDateTime sourceSyncTime;
        private String creator;
        private java.time.LocalDateTime createTime;
        private String updater;
        private java.time.LocalDateTime updateTime;
        private Boolean deleted;
    }

    public interface SourceMapper {
        void selectReceivable(SourceQuery<Void> query, ResultHandler<? super ReceivableSyncSource> handler);
    }

    @Mapper
    public interface TargetMapper {
        record SourceKey(String system,String key) {}
        record ExistingQuery(long tenantId,List<SourceKey> keys,boolean preview) {}
        List<ReceivableSyncRow> selectExisting(@Param("query") ExistingQuery query);
        int insertReceivable(SyncBatch<ReceivableSyncSource,Void> batch);
        int updateReceivable(@Param("row") ReceivableSyncSource row);
    }

}
