package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

/** Missing-only DPPMS labels. Existing CRM products are retained without any update statement. */
@Component
@RequiredArgsConstructor
public class P12SupplementProductSyncTask extends AbstractSyncTask<P12SupplementProductSyncTask.ProductSyncRow,Void> {
    private final TargetMapper products;

    @Override public String taskCode() { return "BASE_P12"; }
    @Override public String taskName() { return "DPPMS缺失产品补齐"; }
    @Override public void work(SyncContext<Void> context) { syncData(context.forData("SupplementProduct",false,false)); }
    /** Resolve all normalized duplicate labels before testing which products are missing. */
    @Override protected void syncDataValidate(SyncBatch<ProductSyncRow,Void> snapshot) {
        var grouped=new LinkedHashMap<String,ProductSyncRow>();
        for(var row:snapshot.rows()) {
            String code=row.getProductCode();
            if(code==null || code.isBlank()) throw new IllegalArgumentException("DPPMS产品编码缺失");
            row.setProductCode(code.trim());
            var previous=grouped.putIfAbsent(row.getProductCode(),row);
            if(previous!=null && (!Objects.equals(previous.getProductName(),row.getProductName())
                    || !Objects.equals(previous.getProductModel(),row.getProductModel())))
                previous.setLabelConflict(true);
        }
        snapshot.rows().clear();
        snapshot.rows().addAll(grouped.values());
    }
    @Override protected void syncDataInsertBefore(SyncBatch<ProductSyncRow,Void> batch) {
        var context=batch.context();
        var productCodes=batch.rows().stream().map(ProductSyncRow::getProductCode).toList();
        var existing=new java.util.ArrayList<ProductSyncRow>();
        for(int offset=0;offset<productCodes.size();offset+=context.batchSize())
            existing.addAll(products.selectSupplementTargets(new TargetMapper.ProductCodesQuery(context.tenantId(),
                    productCodes.subList(offset,Math.min(offset+context.batchSize(),productCodes.size())),context.preview())));
        var codes=new HashSet<String>();
        for(var old:existing) if(!codes.add(old.getProductCode()) || Boolean.TRUE.equals(old.getDeleted())
                || old.getTenantId()!=context.tenantId()) throw new IllegalStateException("已有产品身份冲突");
        for(int index=0;index<batch.rows().size();index++) {
            var source=batch.rows().get(index); source.setSyncAction(SyncWriteAction.UNCHANGED);
            if(codes.contains(source.getProductCode())) continue;
            if(source.isLabelConflict()) throw new IllegalArgumentException("同一DPPMS产品编码有冲突标签");
            checkTextLength(source.getProductCode(),100);
            checkTextLength(source.getProductName(),500);
            checkTextLength(source.getProductModel(),128);
            var row=source.toBuilder().id(context.preview()?null:IdWorker.getId()).tenantId(context.tenantId())
                    .status("ACTIVE").sourceSystem("DPPMS").sourceKey(source.getProductCode()).version(0L).deleted(false)
                    .creator(Long.toString(context.actorId())).updater(Long.toString(context.actorId()))
                    .createTime(context.startedAt()).updateTime(context.startedAt()).syncedAt(context.startedAt()).build();
            row.setSyncAction(SyncWriteAction.INSERT); batch.rows().set(index,row);
        }
    }
    @Override protected SyncCounts syncDataCount(SyncBatch<ProductSyncRow,Void> batch) {
        return SyncCounts.fromActions(batch.rows(),ProductSyncRow::getSyncAction);
    }
    @Override protected void syncDataInsert(SyncBatch<ProductSyncRow,Void> batch) {
        super.syncDataInsert(batch.matching(row->row.getSyncAction()==SyncWriteAction.INSERT));
    }

    @lombok.Data
    @lombok.Builder(toBuilder = true)
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProductSyncRow {
        @lombok.Builder.Default
        private cn.iocoder.yudao.module.pms.integration.api.basesync.SyncWriteAction syncAction=cn.iocoder.yudao.module.pms.integration.api.basesync.SyncWriteAction.UNCHANGED;
        /** Source-only duplicate diagnostic; never persisted. */
        private boolean labelConflict;
        private String productCode;
        private String productModel;
        private String productName;
        private String productDesc;
        private Long id;
        private Long tenantId;
        private String sourceSystem;
        private String sourceKey;
        private String sourceVersion;
        private String status;
        private Long version;
        private java.time.LocalDateTime syncedAt;
        private String creator;
        private String updater;
        private java.time.LocalDateTime createTime;
        private java.time.LocalDateTime updateTime;
        private Boolean deleted;
    }

    public interface SourceMapper {
        List<ProductSyncRow> selectSupplementProduct(SourceQuery<Void> query);
    }

    @Mapper
    public interface TargetMapper {
        record ProductCodesQuery(long tenantId, List<String> productCodes, boolean preview) {}
        List<ProductSyncRow> selectSupplementTargets(@Param("query") ProductCodesQuery query);
        int insertSupplementProduct(SyncBatch<ProductSyncRow,Void> batch);
    }

}
