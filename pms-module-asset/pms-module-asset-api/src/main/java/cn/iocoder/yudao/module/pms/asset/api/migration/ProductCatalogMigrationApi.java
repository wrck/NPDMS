package cn.iocoder.yudao.module.pms.asset.api.migration;

import java.util.List;

/** Official directory and declared business labels; no migration snapshot is a business target. */
public interface ProductCatalogMigrationApi {
    /** Publish through PLT, or validate/reuse a supplied immutable definition revision. */
    Long configureDefinition(Long tenantId, Long actorId, Long existingRevisionId, String correlationId);
    List<Result> preview(Request request);
    List<Result> apply(Request request);
    /** Verify that all original business labels are already carried by a live formal directory entry. */
    List<Snapshot> resolve(ResolveRequest request);

    record Label(String productCode, String productName, String productModel) {}
    record Snapshot(Long productId, String productCode, List<Label> labels) {
        public Snapshot { labels = List.copyOf(labels); }
    }
    record Entry(String sourceKey, Label label, Long targetId, Snapshot lastApplied) {}
    record Request(Long tenantId, Long actorId, Long definitionRevisionId, String correlationId, List<Entry> entries) {}
    record ResolveRequest(Long tenantId, Long actorId, String correlationId, List<Label> labels) {}
    record Result(String sourceKey, Long targetId, String action, Snapshot before, Snapshot after) {}
}
