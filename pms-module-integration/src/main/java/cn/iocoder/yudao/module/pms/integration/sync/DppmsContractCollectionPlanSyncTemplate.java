package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Reads the complete original table; actor identity uses the adjudicated FND-first rule. */
public final class DppmsContractCollectionPlanSyncTemplate {
    private DppmsContractCollectionPlanSyncTemplate() {}
    static final String ADAPTER = "DPPMS_CONTRACT_COLLECTION_PLAN";
    static final String OBJECT = "pm_pb_plan_from_sms";
    static final List<String> ORIGINAL_COLUMNS = List.of("id","contractNo","batchCode","basicDataName",
            "referenceEventName","eventPlanHappenDate","afterDaysNum","eventActualFinishDate","marketingFeedback",
            "createBy","createTime","updateBy","updateTime","effectiveFrom","effectiveTo","dataSource");
    static final List<String> ACTOR_COLUMNS = List.of("creatorFndSourceKey","creatorTSourceKey","updaterFndSourceKey","updaterTSourceKey",
            "creatorFndMatchCount","creatorTMatchCount","updaterFndMatchCount","updaterTMatchCount");
    static final List<String> ACTOR_REFERENCE_COLUMNS = List.of("creatorFndSourceKeys","updaterFndSourceKeys");
    public static SyncDefinition create(Long connectionId) {
        if (connectionId == null || connectionId <= 0) throw new IllegalArgumentException("回款计划来源连接无效");
        var columns = new ArrayList<>(ORIGINAL_COLUMNS); columns.addAll(ACTOR_COLUMNS); columns.addAll(ACTOR_REFERENCE_COLUMNS);
        String sql = DppmsMigrationSql.initial("contract-collection-plans");
        var source = SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).readMode("SQL").sql(sql)
                .parameters(Map.of("planIds","","projectTypes","10","projectIds","")).sourceKey("id").updatedAt(null).columns(List.of()).filters(List.of())
                .mappings(columns.stream().map(name -> SyncDefinition.Mapping.builder().source(name).target(name)
                        .conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false).readStrategy("STREAMING_CURSOR")
                .fetchSize(1000).chunkSize(500).restartPolicy("RESTART_ALL").queryTimeoutSeconds(0)
                .maxRows(10000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
