package cn.iocoder.yudao.module.pms.asset.api.migration;

import java.util.List;

/** Match the official directory by tenant and product code; only missing products are created. */
public interface ProductCatalogMigrationApi {
    List<Result> preview(Request request);
    List<Result> apply(Request request);
    /** Resolve live formal products by code without requiring historical source labels. */
    List<Snapshot> resolve(ResolveRequest request);

    record Label(String productCode, String productName, String productModel) {}
    record Snapshot(Long productId, String productCode, List<Label> labels) {
        public Snapshot { labels = List.copyOf(labels); }
    }
    record Entry(String sourceKey, Label label, Long targetId, Snapshot lastApplied) {}
    record Request(Long tenantId, Long actorId, String correlationId, List<Entry> entries) {}
    record ResolveRequest(Long tenantId, Long actorId, String correlationId, List<Label> labels) {}
    record Result(String sourceKey, Long targetId, String action, Snapshot before, Snapshot after) {}
}
