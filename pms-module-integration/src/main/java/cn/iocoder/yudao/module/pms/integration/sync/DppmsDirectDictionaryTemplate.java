package cn.iocoder.yudao.module.pms.integration.sync;

/** Independent P08 entry; keeps the reviewed dictionary source and exclusion rules. */
public final class DppmsDirectDictionaryTemplate {
    public static final String ADAPTER="DPPMS_DICTIONARIES_DIRECT";
    static final String MISSING_TYPE="fnd_basic_data_missing_type";
    private DppmsDirectDictionaryTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        var original=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P08",connectionId));
        var types=original.sources().getFirst().toBuilder().sql(DppmsMigrationSql.initial("direct-dictionary-types"))
                .sourceKey("id").queryRef(null).groupKey(null).rowProjection(null).build();
        var missing=types.toBuilder().object(MISSING_TYPE).sourceObject("fnd_basic_data").sourceKey("sourceId")
                .sql(DppmsMigrationSql.initial("direct-dictionary-missing-types"))
                .mappings(java.util.stream.Stream.concat(types.mappings().stream(),java.util.stream.Stream.of(
                        SyncDefinition.Mapping.builder().source("sourceId").target("sourceId").conversion("DIRECT").build())).toList()).build();
        var data=DppmsDictionaryDataSyncTemplate.create(connectionId).sources().getFirst();
        return original.toBuilder().adapter(ADAPTER).taskKey("P08_DIRECT").sources(java.util.List.of(types,missing,data)).build();
    }
}
