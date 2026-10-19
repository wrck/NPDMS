package cn.iocoder.yudao.module.pms.integration.basesync.task;

import cn.iocoder.yudao.module.pms.integration.api.basesync.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.util.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;
import org.apache.ibatis.session.ResultHandler;

/** CRM product authority: explicit field aliases and one native MySQL upsert per batch. */
@Component
public class P11CrmProductSyncTask extends DefaultSyncTask<P11CrmProductSyncTask.ProductSyncRow,Void> {
    @Override public String taskCode() { return "BASE_P11"; }
    @Override public String taskName() { return "CRM产品主档"; }
    @Override public String sourceSystem() { return "CRM"; }
    @Override public void work(SyncContext<Void> context) { syncData(context.forData("CrmProduct",false,true)); }
    @Override protected boolean syncDataUsesUpsert(SyncContext<Void> context) { return true; }
    @Override protected void syncDataInsertBefore(SyncBatch<ProductSyncRow,Void> batch) {
        for(var row:batch.rows()) {
            if(row.getCrmStateCode()==null || !Set.of(0,1).contains(row.getCrmStateCode()))
                throw new IllegalArgumentException("CRM产品状态编码未定义");
            validate(row);
            row.setSourceVersion(row.getSourceUpdatedAt()==null?null:row.getSourceUpdatedAt().toString());
            if(!batch.context().preview()) row.setId(IdWorker.getId());
        }
    }

    static void validate(ProductSyncRow row) {
        if(row.getProductCode()==null || row.getProductCode().isBlank()) throw new IllegalArgumentException("产品编码缺失");
        checkTextLength(row.getProductCode(),100); checkTextLength(row.getProductName(),500); checkTextLength(row.getProductModel(),128);
        checkTextLength(row.getProductCategory(),100); checkTextLength(row.getProductType(),100);
        checkTextLength(row.getBusinessUnit(),100); checkTextLength(row.getProductLine(),100);
        exactDecimal(row.getListPrice(),23,10);
        for(var time:Arrays.asList(row.getSourceCreatedAt(),row.getSourceUpdatedAt(),row.getEndOfSaleDate(),
                row.getEndOfSupportDate(),row.getEndOfLifeDate(),row.getLastRenewalDate())) checkMillisecond(time);
    }
    @lombok.Data
    @lombok.Builder(toBuilder = true)
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProductSyncRow {
        private String productCode;
        private String productModel;
        private String productName;
        private String productDesc;
        private String technicalSpec;
        private String productCategory;
        private String productType;
        private java.math.BigDecimal listPrice;
        private Integer crmStatus;
        private String businessUnit;
        private String productLine;
        private Integer orgId;
        private java.time.LocalDateTime sourceCreatedAt;
        private java.time.LocalDateTime sourceUpdatedAt;
        private Integer crmStateCode;
        private Integer crmStatusCode;
        private Integer productStage;
        private java.time.LocalDateTime endOfSaleDate;
        private java.time.LocalDateTime endOfSupportDate;
        private java.time.LocalDateTime endOfLifeDate;
        private java.time.LocalDateTime lastRenewalDate;
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
        void selectCrmProduct(SourceQuery<Void> query, ResultHandler<? super ProductSyncRow> handler);
    }

    @Mapper
    public interface TargetMapper {
        record ProductCodesQuery(long tenantId, List<String> productCodes, boolean preview) {}
        List<ProductSyncRow> selectCrmTargets(@Param("query") ProductCodesQuery query);
        int insertCrmProduct(SyncBatch<ProductSyncRow,Void> batch);
    }

}
