package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Reads every original column; the same streaming pipeline accepts a wider approved code list. */
public final class DppmsProductCatalogSyncTemplate {
    private DppmsProductCatalogSyncTemplate() {}
    static final List<String> ORIGINAL_COLUMNS = List.of("id", "item", "itemname", "describe_");
    public record Request(Long connectionId, List<String> productCodes) {}

    public static SyncDefinition create(Request request) {
        if (request == null) throw new IllegalArgumentException("产品来源选择无效");
        Long connectionId = request.connectionId();
        List<String> codes = request.productCodes();
        if (connectionId == null || connectionId <= 0
                || codes == null || codes.isEmpty() || codes.size() > 10000
                || codes.stream().anyMatch(code -> code == null || code.isBlank() || code.trim().length() > 64)
                || new HashSet<>(codes).size() != codes.size())
            throw new IllegalArgumentException("产品来源编码选择无效");
        Map<String,Object> parameters = new LinkedHashMap<>();
        var placeholders = new ArrayList<String>();
        for (int index=0; index<codes.size(); index++) {
            String name = "productCode"+index;
            parameters.put(name, codes.get(index)); placeholders.add(":"+name);
        }
        var source = SyncDefinition.Source.builder().object("fb_items").sourceObject("fb_items").readMode("SQL")
                .sql("SELECT i.id,i.item,i.itemname,i.describe_ FROM fb_items i WHERE CAST(i.item AS BINARY) IN ("+String.join(",",placeholders)+")")
                .parameters(parameters).sourceKey("item").updatedAt(null).columns(List.of()).filters(List.of())
                .mappings(ORIGINAL_COLUMNS.stream().map(column -> SyncDefinition.Mapping.builder().target(column).source(column)
                        .conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter("DPPMS_PRODUCT_CATALOG").sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false)
                .readStrategy("STREAMING_CURSOR").fetchSize(2000).chunkSize(1000).restartPolicy("RESTART_ALL")
                .queryTimeoutSeconds(0).maxRows(2000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
