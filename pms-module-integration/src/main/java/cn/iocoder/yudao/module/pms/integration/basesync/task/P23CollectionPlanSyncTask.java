package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class P23CollectionPlanSyncTask extends AbstractSyncTask<P23CollectionPlanSyncTask.CollectionPlanCacheSource,P23CollectionPlanSyncTask.Parameters> {
    private final TargetMapper mapper;

    @Override public String taskCode() { return "BASE_P23"; }
    @Override public String taskName() { return "合同收款计划展示缓存"; }
    @Override public void work(SyncContext<Parameters> context) { syncData(context.forData("CollectionPlan",true,false)); }
    public record Parameters(Set<String> nullableColumns) {}
    @Override protected Parameters createParameters(SyncContext<?> runtime) {
        return new Parameters(Set.copyOf(mapper.nullableColumns()));
    }
    @Override protected void syncDataValidate(SyncBatch<CollectionPlanCacheSource,Parameters> batch) {
        for(var source:batch.rows()) {
            { var value=source;
                var row=new CollectionPlanCacheRow();
                row.setContractNo(value.getContractNo());
                checkTextLength(row.getContractNo(),64);
                row.setBatchCode(value.getBatchCode());
                checkTextLength(row.getBatchCode(),32);
                row.setCollectionItemName(value.getCollectionItemName());
                checkTextLength(row.getCollectionItemName(),64);
                row.setReferenceEventName(value.getReferenceEventName());
                checkTextLength(row.getReferenceEventName(),64);
                row.setEventPlannedAt(dateTime(value.getEventPlannedAt()));
                row.setAfterDays(integer(value.getAfterDays()));
                row.setEventActualFinishedAt(dateTime(value.getEventActualFinishedAt()));
                row.setMarketingFeedback(value.getMarketingFeedback());
                checkTextLength(row.getMarketingFeedback(),2000);
                row.setCreator(value.getCreator());
                checkTextLength(row.getCreator(),64);
                row.setCreateTime(dateTime(value.getCreateTime()));
                row.setUpdater(value.getUpdater());
                checkTextLength(row.getUpdater(),64);
                row.setUpdateTime(dateTime(value.getUpdateTime()));
                row.setEffectiveFrom(dateTime(value.getEffectiveFrom()));
                row.setEffectiveTo(dateTime(value.getEffectiveTo()));
                if((row.getCreateTime()==null && !batch.context().parameters().nullableColumns().contains("create_time"))
                        || (row.getUpdateTime()==null && !batch.context().parameters().nullableColumns().contains("update_time")))
                    throw new IllegalStateException("P23：原时间缺失且目标列不允许空值；禁止补造时间");
                row.setDataSource(value.getDataSource());
                checkTextLength(row.getDataSource(),25);
                value.setTarget(row);
            }
        }
    }
    @Override protected void syncDataInsertBefore(SyncBatch<CollectionPlanCacheSource,Parameters> batch) {
        var context=batch.context();
        for(var source:batch.rows()) {
            { var value=source;
                var row=value.getTarget();
                row.setId(context.preview()?null:IdWorker.getId()); row.setTenantId(context.tenantId()); row.setDeleted(false);
            }
        }
    }
    @Override protected long syncDataClearCount(SyncContext<Parameters> context) {
        var query=new TargetMapper.TenantCacheQuery(context.tenantId(),context.preview());
        return mapper.countCollectionPlan(query);
    }
    @Override protected long syncDataClear(SyncContext<Parameters> context) {
        var query=new TargetMapper.TenantCacheQuery(context.tenantId(),context.preview());
        return mapper.deleteCollectionPlan(query);
    }

    private static Integer integer(String value) { return value==null?null:new java.math.BigDecimal(value).intValueExact(); }
    private static java.time.LocalDateTime dateTime(String value) {
        if(value==null) return null;
        var date=java.time.LocalDateTime.parse(value.replace(' ','T'));
        checkMillisecond(date);
        return date;
    }

    @lombok.Data
    public static final class CollectionPlanCacheSource {
        private CollectionPlanCacheRow target;
        private String contractNo;
        private String batchCode;
        private String collectionItemName;
        private String referenceEventName;
        private String eventPlannedAt;
        private String afterDays;
        private String eventActualFinishedAt;
        private String marketingFeedback;
        private String creator;
        private String createTime;
        private String updater;
        private String updateTime;
        private String effectiveFrom;
        private String effectiveTo;
        private String dataSource;
    }

    @lombok.Data
    public static class CollectionPlanCacheRow {
        private Long id;
        private Long tenantId;
        private String contractNo;
        private String batchCode;
        private String collectionItemName;
        private String referenceEventName;
        private java.time.LocalDateTime eventPlannedAt;
        private Integer afterDays;
        private java.time.LocalDateTime eventActualFinishedAt;
        private String marketingFeedback;
        private String creator;
        private java.time.LocalDateTime createTime;
        private String updater;
        private java.time.LocalDateTime updateTime;
        private java.time.LocalDateTime effectiveFrom;
        private java.time.LocalDateTime effectiveTo;
        private String dataSource;
        private Boolean deleted;
    }

    public interface SourceMapper {
        List<CollectionPlanCacheSource> selectCollectionPlan(SourceQuery<Parameters> query);
    }

    @Mapper
    public interface TargetMapper {
        record TenantCacheQuery(long tenantId, boolean preview) {}
        @InterceptorIgnore(tenantLine="true",dataPermission="true")
        List<String> nullableColumns();
        long countCollectionPlan(@Param("query") TenantCacheQuery query);
        int deleteCollectionPlan(@Param("query") TenantCacheQuery query);
        int insertCollectionPlan(SyncBatch<CollectionPlanCacheSource,Parameters> batch);
    }

}
