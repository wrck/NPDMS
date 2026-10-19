package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Full reviewed supplementary directory, using the approved product-code identity; the nullable id is retained as raw evidence. */
public final class DppmsDirectProductTemplate {
    public static final String ADAPTER="DPPMS_PRODUCT_CATALOG_DIRECT";
    private DppmsDirectProductTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("产品来源连接无效");
        var source=SyncDefinition.Source.builder().object("fb_items").sourceObject("fb_items").readMode("SQL").sourceKey("item")
                .sql("SELECT i.id,i.item,i.itemname,i.describe_ FROM fb_items i WHERE (:productCodes='' OR FIND_IN_SET(i.item,:productCodes))")
                .parameters(Map.of("productCodes","")).updatedAt(null).columns(List.of()).filters(List.of())
                .mappings(DppmsProductCatalogSyncTemplate.ORIGINAL_COLUMNS.stream().map(column->SyncDefinition.Mapping.builder()
                        .target(column).source(column).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).taskKey("P12_DIRECT").sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).readStrategy("STREAMING_CURSOR").fetchSize(2000).chunkSize(1000)
                .restartPolicy("RESTART_ALL").maxRows(2000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
