package cn.iocoder.yudao.module.pms.integration.sync;
/** Approved grouped input on the original adapter; the verified historical factory stays unchanged. */
public final class DppmsGroupedProjectContractSyncTemplate {
    private DppmsGroupedProjectContractSyncTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        var original=DppmsProjectContractRelationSyncTemplate.create(connectionId);
        return original.toBuilder().sources(original.sources().stream().map(source->source.toBuilder()
                .sql(DppmsMigrationSql.initial("project-contract-grouped")).groupKey("migrationKey")
                .rowProjection(DppmsProjectContractGroupProjection.PROJECTION).build()).toList()).build();
    }
}
