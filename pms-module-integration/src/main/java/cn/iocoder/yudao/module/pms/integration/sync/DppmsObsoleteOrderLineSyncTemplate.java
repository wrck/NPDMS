package cn.iocoder.yudao.module.pms.integration.sync;
import java.util.*;
public final class DppmsObsoleteOrderLineSyncTemplate {
    private DppmsObsoleteOrderLineSyncTemplate(){}
    static final String ADAPTER="DPPMS_OBSOLETE_ORDER_LINE_BINDING", OBJECT="pm_order_line_from_erp";
    public static SyncDefinition create(Long connectionId,Long originalTaskId) {
        if(connectionId==null || connectionId<=0 || originalTaskId==null || originalTaskId<=0)
            throw new IllegalArgumentException("原订单迁移任务和来源连接必填");
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).sourceKey("id").readMode("SQL")
                .sql(DppmsMigrationSql.initial("obsolete-company01-line-bindings")).parameters(Map.of()).columns(List.of()).filters(List.of())
                .mappings(List.of(SyncDefinition.Mapping.builder().source("id").target("id").conversion("DIRECT").build(),
                        SyncDefinition.Mapping.builder().source("compCode").target("compCode").conversion("DIRECT").build(),
                        SyncDefinition.Mapping.builder().target("_originalTaskId").conversion("CONSTANT").constant(originalTaskId.toString()).build())).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .retryCount(0).retryIntervalSeconds(60).readStrategy("STREAMING_CURSOR").fetchSize(1000).chunkSize(500)
                .maxRows(500).maxBytes(4L*1024*1024).sources(List.of(source)).build();
    }
}
