package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.asset.api.migration.DeviceWarrantyRenewalMigrationApi;
import java.util.List;
import java.util.Map;

public final class DppmsWarrantyRenewalSyncTemplate {
    private DppmsWarrantyRenewalSyncTemplate() {}
    static final String ADAPTER = "DPPMS_WARRANTY_RENEWAL", OBJECT = "fb_service";

    public static SyncDefinition create(Long connectionId) {
        if (connectionId == null || connectionId <= 0) throw new IllegalArgumentException("续保来源连接无效");
        var source = SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT)
                .sourceKey("renewalSourceKey").readMode("SQL")
                .sql(DppmsMigrationSql.initial("warranty-renewals"))
                .parameters(Map.of("projectTypes", "10", "projectIds", "", "deviceSns", ""))
                .columns(List.of()).filters(List.of())
                .mappings(DeviceWarrantyRenewalMigrationApi.ORIGINAL_COLUMNS.stream().map(name ->
                        SyncDefinition.Mapping.builder().source(name).target(name).conversion("DIRECT").build()).toList()).build();
        // fb_service has no modification time. Scheduled synchronization compares a full snapshot, never a fake watermark.
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false)
                .readStrategy("STREAMING_CURSOR").fetchSize(1000).chunkSize(500).restartPolicy("RESTART_ALL")
                .maxRows(10000).maxBytes(64L * 1024 * 1024).queryTimeoutSeconds(0).sources(List.of(source)).build();
    }
}
