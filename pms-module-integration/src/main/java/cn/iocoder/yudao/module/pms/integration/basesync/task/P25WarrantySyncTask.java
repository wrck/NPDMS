package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.*;
import java.util.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;
import org.apache.ibatis.session.ResultHandler;

@Component
@RequiredArgsConstructor
public class P25WarrantySyncTask extends AbstractSyncTask<P25WarrantySyncTask.WarrantySyncSource,Void> {
    private final TargetMapper records;

    @Override public String taskCode() { return "BASE_P25"; }
    @Override public String taskName() { return "设备续保历史"; }
    @Override public Set<String> requiredPermissions() { return Set.of("pms:device:query"); }
    @Override public void work(SyncContext<Void> context) { syncData(context.forData("Warranty",false,true)); }
    @Override protected void syncDataQueryByRowHandler(SyncContext<Void> context, ResultHandler<WarrantySyncSource> handler) {
        try(var source=context.source().open(context.connectionId())){
            var duplicates=syncDataQueryBySql(source.sqlSession(),new MappedCall<>(context,
                    sourceMapperNamespace()+".selectDuplicateIdentity",new SourceQuery<>(context.maxRows()+1,context.tenantId(),context.preview(),null)));
            if(!duplicates.isEmpty())throw new IllegalArgumentException("原续保复合来源身份重复；禁止合并或跳过原记录");
            syncDataQueryBySql(source.sqlSession(),sourceQuery(context),(ResultHandler<WarrantySyncSource>)result->{
                    var row=result.getResultObject();
                    if(row.getSourceId()==null || row.getSourceId().isBlank()) throw new IllegalArgumentException("原续保ID缺失");
                    row.setSourceKey("fb_service:"+row.getSourceId()+":"+(row.getDeviceSn()==null?"":row.getDeviceSn()));
                    if(row.getSourceKey().length()>128) throw new IllegalArgumentException("原续保身份过长");
                    handler.handleResult(result);
                });
        }
    }
    @Override protected void syncDataInsertBefore(SyncBatch<WarrantySyncSource,Void> batch) {
        var context=batch.context();
        var byKey=context.identities().findByKeys(new SyncIdentityStore.Query("WARRANTY",batch.rows().stream().map(WarrantySyncSource::getSourceKey).toList()));
        var identities=byKey.values();
        var targets=identities.isEmpty()?List.<WarrantySyncRow>of():records.selectExisting(new TargetMapper.ExistingQuery(context.tenantId(),identities.stream().map(SyncIdentityStore.Identity::targetId).toList(),context.preview()));
        var byId=new HashMap<Long,WarrantySyncRow>();
        for(var target:targets) if(byId.put(target.getId(),target)!=null) throw new IllegalStateException("续保目标身份重复");
        for(var source:batch.rows()) {
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            var row=convert(source);
            var identity=byKey.get(source.getSourceKey());
            var old=identity==null?null:byId.get(identity.targetId());
            if(identity!=null && (old==null || Boolean.TRUE.equals(old.getDeleted()) || old.getTenantId()!=context.tenantId()
                    || old.getVersion()==null || !Objects.equals(identity.fingerprint(),fingerprint(old))))
                throw new IllegalStateException("续保目标或来源基线已变化，禁止覆盖");
            if(old!=null && business(old).equals(business(row))) { continue; }
            row.setId(old==null?(context.preview()?null:IdWorker.getId()):old.getId()); row.setTenantId(context.tenantId());
            row.setCreator(old==null?Long.toString(context.actorId()):old.getCreator()); row.setCreateTime(old==null?context.startedAt():old.getCreateTime());
            row.setUpdater(Long.toString(context.actorId())); row.setUpdateTime(context.startedAt()); row.setDeleted(false);
            row.setVersion(old==null?0:Math.addExact(old.getVersion(),1));
            source.setTarget(row); source.setExpectedVersion(old==null?null:old.getVersion());
            source.setPreviousFingerprint(identity==null?null:identity.fingerprint());
            source.setSyncAction(old==null?SyncWriteAction.INSERT:SyncWriteAction.UPDATE);
        }
    }
    @Override protected SyncCounts syncDataCount(SyncBatch<WarrantySyncSource,Void> batch) {
        return SyncCounts.fromActions(batch.rows(),WarrantySyncSource::getSyncAction);
    }
    @Override protected void syncDataInsert(SyncBatch<WarrantySyncSource,Void> batch) {
        super.syncDataInsert(batch.matching(row->row.getSyncAction()==SyncWriteAction.INSERT));
        super.syncDataUpdate(batch.matching(row->row.getSyncAction()==SyncWriteAction.UPDATE));
    }
    @Override protected void syncDataInsertAfter(SyncBatch<WarrantySyncSource,Void> batch) {
        batch.context().identities().saveAll(batch.rows().stream()
                .filter(row->row.getSyncAction()!=SyncWriteAction.UNCHANGED).map(this::identity).toList());
    }
    private SyncIdentityStore.Save identity(WarrantySyncSource source) {
        var row=source.getTarget();
        return new SyncIdentityStore.Save("WARRANTY",source.getSourceKey(),row.getId(),fingerprint(row),source.getPreviousFingerprint());
    }


    static WarrantySyncRow convert(WarrantySyncSource source) {
        var row=WarrantySyncRow.builder().deviceSn(source.getDeviceSn()).warrantyContractNo(source.getWarrantyContractNo())
                .warrantyGrade(source.getWarrantyGrade()).warrantyStartDate(date(source.getWarrantyStartDate()))
                .warrantyEndDate(date(source.getWarrantyEndDate())).remark(source.getRemark()).build();
        checkTextLength(row.getDeviceSn(),100); checkTextLength(row.getWarrantyContractNo(),64); checkTextLength(row.getWarrantyGrade(),32);
        if(source.getWarrantyYears()!=null && !source.getWarrantyYears().isEmpty()) {
            if(!source.getWarrantyYears().matches("[0-9]")) throw new IllegalArgumentException("原续保参考年数无效");
            row.setWarrantyMonths(Integer.parseInt(source.getWarrantyYears())*12);
        }
        row.setExtended(flag(source.getExtended(),true)); row.setIsLatest(flag(source.getIsLatest(),false));
        if(row.getWarrantyStartDate()!=null && row.getWarrantyEndDate()!=null && row.getWarrantyEndDate().isBefore(row.getWarrantyStartDate()))
            throw new IllegalArgumentException("原续保日期倒置");
        checkTextCapacity(row.getRemark());
        return row;
    }
    private static LocalDate date(String value) {
        if(value==null) return null;
        if(value.length()==10) return LocalDate.parse(value);
        var date=LocalDateTime.parse(value.replace(' ','T'));
        if(!date.toLocalTime().equals(LocalTime.MIDNIGHT)) throw new IllegalArgumentException("续保日期包含不能丢弃的非零时间");
        return date.toLocalDate();
    }
    private static Boolean flag(String value,boolean zeroAllowed) {
        if(value==null) return null;
        if("1".equals(value)) return true;
        if(zeroAllowed && "0".equals(value)) return false;
        throw new IllegalArgumentException("未定义的原续保标记");
    }

    static List<Object> business(WarrantySyncRow row) {
        return Arrays.asList(row.getDeviceSn(),row.getWarrantyContractNo(),row.getWarrantyGrade(),row.getWarrantyStartDate(),row.getWarrantyEndDate(),
                row.getWarrantyMonths(),row.getExtended(),row.getIsLatest(),row.getRemark());
    }
    static String fingerprint(WarrantySyncRow row) {
        var values=new ArrayList<Object>(business(row));
        values.addAll(Arrays.asList(row.getId(),row.getTenantId(),row.getVersion(),row.getCreator(),row.getCreateTime(),row.getUpdater(),row.getUpdateTime(),row.getDeleted()));
        return SyncFingerprint.of(values);
    }

    @lombok.Data
    public static class WarrantySyncSource {
        private WarrantySyncRow target;
        private cn.iocoder.yudao.module.pms.integration.api.basesync.SyncWriteAction syncAction;
        private Integer expectedVersion;
        private String previousFingerprint;
        private String sourceKey;
        private String sourceId;
        private String warrantyContractNo;
        private String deviceSn;
        private String warrantyGrade;
        private String warrantyStartDate;
        private String warrantyEndDate;
        private String warrantyYears;
        private String remark;
        private String extended;
        private String isLatest;
    }

    @lombok.Data
    @lombok.Builder(toBuilder=true)
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WarrantySyncRow {
        private Long id;
        private Long tenantId;
        private String deviceSn;
        private String warrantyContractNo;
        private String warrantyGrade;
        private java.time.LocalDate warrantyStartDate;
        private java.time.LocalDate warrantyEndDate;
        private Integer warrantyMonths;
        private Boolean extended;
        private Boolean isLatest;
        private String remark;
        private Integer version;
        private String creator;
        private java.time.LocalDateTime createTime;
        private String updater;
        private java.time.LocalDateTime updateTime;
        private Boolean deleted;
    }

    public interface SourceMapper {
        List<Integer> selectDuplicateIdentity(SourceQuery<Void> query);
        void selectWarranty(SourceQuery<Void> query, ResultHandler<? super WarrantySyncSource> handler);
    }

    @Mapper
    public interface TargetMapper {
        record ExistingQuery(long tenantId,List<Long> ids,boolean preview) {}
        List<WarrantySyncRow> selectExisting(@Param("query") ExistingQuery query);
        int insertWarranty(SyncBatch<WarrantySyncSource,Void> batch);
        int updateWarranty(@Param("row") WarrantySyncSource row);
    }

}
