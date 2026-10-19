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
public class P34FactoryHistorySyncTask extends AbstractSyncTask<P34FactoryHistorySyncTask.FactorySyncSource,P34FactoryHistorySyncTask.Parameters> {
    private final TargetMapper records;

    @Override public String taskCode() { return "BASE_P34"; }
    @Override public String taskName() { return "出厂版本历史"; }
    @Override public void work(SyncContext<Parameters> context) { syncData(context.forData("FactoryHistory",false,true)); }
    public record Parameters(boolean nullableSerial) {}
    @Override protected Parameters createParameters(SyncContext<?> runtime) {
        return new Parameters(records.nullableSerialColumn()==1);
    }
    @Override protected void syncDataQueryByRowHandler(SyncContext<Parameters> context, ResultHandler<FactorySyncSource> handler) {
        String[] previous={null};
        int[] occurrence={0};
        super.syncDataQueryByRowHandler(context,result->{
                    var row=result.getResultObject();
                    var values=Arrays.asList(row.getDeviceSn(),row.getConpVersion(),row.getCpldVersion(),row.getBootVersion(),row.getPcbVersion());
                    for(var value:values) checkTextLength(value,100);
                    String digest=SyncFingerprint.of(values);
                    occurrence[0]=digest.equals(previous[0])?Math.addExact(occurrence[0],1):1;
                    previous[0]=digest;
                    row.setSourceKey(digest+":"+occurrence[0]);
                    handler.handleResult(result);
                });
    }
    @Override protected void syncDataInsertBefore(SyncBatch<FactorySyncSource,Parameters> batch) {
        var context=batch.context();
        var byKey=context.identities().findByKeys(new SyncIdentityStore.Query("FACTORY_HISTORY",batch.rows().stream().map(FactorySyncSource::getSourceKey).toList()));
        var identities=byKey.values();
        var targets=identities.isEmpty()?List.<FactorySyncRow>of():records.selectExisting(new TargetMapper.ExistingQuery(context.tenantId(),identities.stream().map(SyncIdentityStore.Identity::targetId).toList(),context.preview()));
        var byId=new HashMap<Long,FactorySyncRow>();
        for(var row:targets) if(byId.put(row.getId(),row)!=null) throw new IllegalStateException("出厂目标身份重复");
        for(var source:batch.rows()) {
            if(source.getDeviceSn()==null && !context.parameters().nullableSerial())
                throw new IllegalStateException("P34原SN缺失且目标列不允许空值；禁止填占位或跳过");
            source.setSyncAction(SyncWriteAction.UNCHANGED);
            var identity=byKey.get(source.getSourceKey());
            if(identity!=null) {
                var old=byId.get(identity.targetId());
                if(old==null || Boolean.TRUE.equals(old.getDeleted()) || old.getTenantId()!=context.tenantId()
                        || old.getProjectId()!=null || !Objects.equals(identity.fingerprint(),fingerprint(old))
                        || !Arrays.asList(source.getDeviceSn(),source.getConpVersion(),source.getCpldVersion(),source.getBootVersion(),source.getPcbVersion())
                        .equals(Arrays.asList(old.getDeviceSn(),old.getConpVersion(),old.getCpldVersion(),old.getBootVersion(),old.getPcbVersion())))
                    throw new IllegalStateException("出厂不可变历史或来源绑定已变化");
                continue;
            }
            var row=FactorySyncRow.builder().id(context.preview()?null:IdWorker.getId()).tenantId(context.tenantId())
                    .deviceSn(source.getDeviceSn()).conpVersion(source.getConpVersion()).cpldVersion(source.getCpldVersion())
                    .bootVersion(source.getBootVersion()).pcbVersion(source.getPcbVersion()).projectId(null)
                    .creator(Long.toString(context.actorId())).updater(Long.toString(context.actorId()))
                    .createTime(context.startedAt()).updateTime(context.startedAt()).deleted(false).build();
            source.setTarget(row); source.setSyncAction(SyncWriteAction.INSERT);
        }
    }
    @Override protected SyncCounts syncDataCount(SyncBatch<FactorySyncSource,Parameters> batch) {
        return SyncCounts.fromActions(batch.rows(),FactorySyncSource::getSyncAction);
    }
    @Override protected void syncDataInsert(SyncBatch<FactorySyncSource,Parameters> batch) {
        super.syncDataInsert(batch.matching(row->row.getSyncAction()==SyncWriteAction.INSERT));
    }
    @Override protected void syncDataInsertAfter(SyncBatch<FactorySyncSource,Parameters> batch) {
        batch.context().identities().saveAll(batch.rows().stream()
                .filter(row->row.getSyncAction()!=SyncWriteAction.UNCHANGED).map(this::identity).toList());
    }
    private SyncIdentityStore.Save identity(FactorySyncSource source) {
        var row=source.getTarget();
        return new SyncIdentityStore.Save("FACTORY_HISTORY",source.getSourceKey(),row.getId(),fingerprint(row),null);
    }


    static String fingerprint(FactorySyncRow row) {
        return SyncFingerprint.of(Arrays.asList(row.getId(),row.getTenantId(),row.getProjectId(),row.getDeviceSn(),row.getConpVersion(),
                row.getCpldVersion(),row.getBootVersion(),row.getPcbVersion(),row.getCreator(),row.getCreateTime(),row.getUpdater(),row.getUpdateTime(),row.getDeleted()));
    }

    @lombok.Data
    public static class FactorySyncSource {
        private FactorySyncRow target;
        private cn.iocoder.yudao.module.pms.integration.api.basesync.SyncWriteAction syncAction;
        private String sourceKey;
        private String deviceSn;
        private String conpVersion;
        private String cpldVersion;
        private String bootVersion;
        private String pcbVersion;
    }

    @lombok.Data
    @lombok.Builder(toBuilder=true)
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FactorySyncRow {
        private Long id;
        private Long tenantId;
        private Long projectId;
        private String deviceSn;
        private String conpVersion;
        private String bootVersion;
        private String cpldVersion;
        private String pcbVersion;
        private String creator;
        private java.time.LocalDateTime createTime;
        private String updater;
        private java.time.LocalDateTime updateTime;
        private Boolean deleted;
    }

    public interface SourceMapper {
        void selectFactoryHistory(SourceQuery<Parameters> query, ResultHandler<? super FactorySyncSource> handler);
    }

    @Mapper
    public interface TargetMapper {
        record ExistingQuery(long tenantId,List<Long> ids,boolean preview) {}
        List<FactorySyncRow> selectExisting(@Param("query") ExistingQuery query);
        int insertFactoryHistory(SyncBatch<FactorySyncSource,Parameters> batch);
        @InterceptorIgnore(tenantLine="true",dataPermission="true")
        int nullableSerialColumn();
    }

}
