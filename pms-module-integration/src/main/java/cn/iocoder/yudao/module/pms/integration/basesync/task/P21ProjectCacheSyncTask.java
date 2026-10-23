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
public class P21ProjectCacheSyncTask extends AbstractSyncTask<P21ProjectCacheSyncTask.ProjectCacheSource,Void> {
    private final TargetMapper mapper;

    @Override public String taskCode() { return "BASE_P21"; }
    @Override public String taskName() { return "租赁与配置展示缓存"; }
    @Override public void work(SyncContext<Void> context) { syncData(context.forData("ProjectCache",true,false)); }
    @Override protected void syncDataBefore(SyncContext<Void> context) {
        super.syncDataBefore(context);
        if(mapper.nullableColumns()!=2) {
            if(!context.preview()) throw new IllegalStateException("P21：已审核可空列尚未就绪；禁止补造关联ID或原时间");
            context.runState().warning("P21_EXECUTION_SCHEMA_REQUIRED:project_id_nullable");
        }
    }
    @Override protected List<ProjectCacheSource> syncDataQuery(SyncContext<Void> context) {
        try(var source=context.source().open(context.connectionId())) {
            var query=new SourceQuery<>(context.maxRows()+1,context.tenantId(),context.preview(),null);
            var rows=new ArrayList<ProjectCacheSource>();
            rows.addAll(syncDataQueryBySql(source.sqlSession(),new MappedCall<>(context,sourceMapperNamespace()+".selectLease",query)));
            rows.addAll(syncDataQueryBySql(source.sqlSession(),new MappedCall<>(context,sourceMapperNamespace()+".selectConfiguration",query)));
            return rows;
        }
    }
    @Override protected void syncDataValidate(SyncBatch<ProjectCacheSource,Void> batch) {
        for(var source:batch.rows()) {
            if(source instanceof LeaseCacheSource value) {
                var row=new LeaseCacheRow();
                row.setProjectCode(value.getProjectCode());
                checkTextCapacity(row.getProjectCode());
                row.setExecutionNo(value.getExecutionNo());
                checkTextCapacity(row.getExecutionNo());
                row.setProductCategory(value.getProductCategory());
                checkTextCapacity(row.getProductCategory());
                row.setProductType(value.getProductType());
                checkTextCapacity(row.getProductType());
                row.setProductCode(value.getProductCode());
                checkTextCapacity(row.getProductCode());
                row.setProductModel(value.getProductModel());
                checkTextCapacity(row.getProductModel());
                row.setProductName(value.getProductName());
                checkTextCapacity(row.getProductName());
                row.setQuantity(exactDecimal(value.getQuantity()==null?null:new java.math.BigDecimal(value.getQuantity()),24,6));
                row.setMemo(value.getMemo());
                checkTextCapacity(row.getMemo());
                row.setLeaseDuration(exactDecimal(value.getLeaseDuration()==null?null:new java.math.BigDecimal(value.getLeaseDuration()),16,2));
                row.setDataSource(value.getDataSource());
                checkTextLength(row.getDataSource(),25);
                value.setTarget(row);
            }
            if(source instanceof ConfigurationCacheSource value) {
                var row=new ConfigurationCacheRow();
                row.setProjectCode(value.getProjectCode());
                checkTextCapacity(row.getProjectCode());
                row.setExecutionNo(value.getExecutionNo());
                checkTextCapacity(row.getExecutionNo());
                row.setItemGroup(integer(value.getItemGroup()));
                row.setProductCode(value.getProductCode());
                checkTextCapacity(row.getProductCode());
                row.setParentCode(value.getParentCode());
                checkTextCapacity(row.getParentCode());
                row.setQuantity(exactDecimal(value.getQuantity()==null?null:new java.math.BigDecimal(value.getQuantity()),24,6));
                row.setBomPath(value.getBomPath());
                checkTextCapacity(row.getBomPath());
                row.setProductModel(value.getProductModel());
                checkTextCapacity(row.getProductModel());
                row.setProductDescription(value.getProductDescription());
                checkTextCapacity(row.getProductDescription());
                row.setLevel(integer(value.getLevel()));
                row.setDataSource(value.getDataSource());
                checkTextLength(row.getDataSource(),25);
                value.setTarget(row);
            }
        }
    }
    @Override protected void syncDataInsertBefore(SyncBatch<ProjectCacheSource,Void> batch) {
        var context=batch.context();
        for(var source:batch.rows()) {
            if(source instanceof LeaseCacheSource value) {
                var row=value.getTarget();
                row.setId(context.preview()?null:IdWorker.getId()); row.setTenantId(context.tenantId()); row.setDeleted(false);
                row.setCreator(Long.toString(context.actorId())); row.setUpdater(Long.toString(context.actorId()));
                row.setCreateTime(context.startedAt()); row.setUpdateTime(context.startedAt());
            }
            if(source instanceof ConfigurationCacheSource value) {
                var row=value.getTarget();
                row.setId(context.preview()?null:IdWorker.getId()); row.setTenantId(context.tenantId()); row.setDeleted(false);
                row.setCreator(Long.toString(context.actorId())); row.setUpdater(Long.toString(context.actorId()));
                row.setCreateTime(context.startedAt()); row.setUpdateTime(context.startedAt());
            }
        }
    }
    @Override protected void syncDataInsert(SyncBatch<ProjectCacheSource,Void> batch) {
        var lease=batch.rows().stream().filter(LeaseCacheSource.class::isInstance).map(LeaseCacheSource.class::cast).map(LeaseCacheSource::getTarget).toList();
        if(!lease.isEmpty() && mapper.insertLease(lease)!=lease.size()) throw new IllegalStateException("缓存插入数量不符");
        var configuration=batch.rows().stream().filter(ConfigurationCacheSource.class::isInstance).map(ConfigurationCacheSource.class::cast).map(ConfigurationCacheSource::getTarget).toList();
        if(!configuration.isEmpty() && mapper.insertConfiguration(configuration)!=configuration.size()) throw new IllegalStateException("缓存插入数量不符");
    }
    @Override protected long syncDataClearCount(SyncContext<Void> context) {
        var query=new TargetMapper.TenantCacheQuery(context.tenantId(),context.preview());
        return mapper.countLease(query) + mapper.countConfiguration(query);
    }
    @Override protected long syncDataClear(SyncContext<Void> context) {
        var query=new TargetMapper.TenantCacheQuery(context.tenantId(),context.preview());
        return mapper.deleteLease(query) + mapper.deleteConfiguration(query);
    }

    private static Integer integer(String value) { return value==null?null:new java.math.BigDecimal(value).intValueExact(); }

    public sealed interface ProjectCacheSource permits LeaseCacheSource, ConfigurationCacheSource {}

    @lombok.Data
    public static final class LeaseCacheSource implements ProjectCacheSource {
        private LeaseCacheRow target;
        private String projectCode;
        private String executionNo;
        private String productCategory;
        private String productType;
        private String productCode;
        private String productModel;
        private String productName;
        private String quantity;
        private String memo;
        private String leaseDuration;
        private String dataSource;
    }

    @lombok.Data
    public static class LeaseCacheRow {
        private Long id;
        private Long tenantId;
        private String projectCode;
        private String executionNo;
        private String productCategory;
        private String productType;
        private String productCode;
        private String productModel;
        private String productName;
        private java.math.BigDecimal quantity;
        private String memo;
        private java.math.BigDecimal leaseDuration;
        private String dataSource;
        private String creator;
        private java.time.LocalDateTime createTime;
        private String updater;
        private java.time.LocalDateTime updateTime;
        private Boolean deleted;
    }

    @lombok.Data
    public static final class ConfigurationCacheSource implements ProjectCacheSource {
        private ConfigurationCacheRow target;
        private String projectCode;
        private String executionNo;
        private String itemGroup;
        private String productCode;
        private String parentCode;
        private String quantity;
        private String bomPath;
        private String productModel;
        private String productDescription;
        private String level;
        private String dataSource;
    }

    @lombok.Data
    public static class ConfigurationCacheRow {
        private Long id;
        private Long tenantId;
        private String projectCode;
        private String executionNo;
        private Integer itemGroup;
        private String productCode;
        private String parentCode;
        private java.math.BigDecimal quantity;
        private String bomPath;
        private String productModel;
        private String productDescription;
        private Integer level;
        private String dataSource;
        private String creator;
        private java.time.LocalDateTime createTime;
        private String updater;
        private java.time.LocalDateTime updateTime;
        private Boolean deleted;
    }

    public interface SourceMapper {
        List<LeaseCacheSource> selectLease(SourceQuery<Void> query);
        List<ConfigurationCacheSource> selectConfiguration(SourceQuery<Void> query);
    }

    @Mapper
    public interface TargetMapper {
        record TenantCacheQuery(long tenantId, boolean preview) {}
        @InterceptorIgnore(tenantLine="true",dataPermission="true")
        int nullableColumns();
        long countLease(@Param("query") TenantCacheQuery query);
        int deleteLease(@Param("query") TenantCacheQuery query);
        int insertLease(@Param("rows") List<LeaseCacheRow> rows);
        long countConfiguration(@Param("query") TenantCacheQuery query);
        int deleteConfiguration(@Param("query") TenantCacheQuery query);
        int insertConfiguration(@Param("rows") List<ConfigurationCacheRow> rows);
    }

}
