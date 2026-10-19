package cn.iocoder.yudao.module.pms.integration.sync;
import java.util.Map;
/** Local qualification for the original adapter; never changes the independent direct template. */
public final class DppmsLocalWarrantyRenewalSyncTemplate {
    private DppmsLocalWarrantyRenewalSyncTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        var original=DppmsWarrantyRenewalSyncTemplate.create(connectionId);
        return original.toBuilder().sources(original.sources().stream().map(source->source.toBuilder()
                .sql(DppmsMigrationSql.initial("warranty-renewals-local-eligibility"))
                .parameters(Map.of("deviceSns","")).build()).toList()).build();
    }
}
