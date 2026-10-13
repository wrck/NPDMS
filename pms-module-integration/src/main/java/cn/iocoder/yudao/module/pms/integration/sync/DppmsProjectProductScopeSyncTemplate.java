package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import java.util.*;

public final class DppmsProjectProductScopeSyncTemplate {
    private DppmsProjectProductScopeSyncTemplate() {}
    static final String ADAPTER="DPPMS_PROJECT_PRODUCT_SCOPE",OBJECT="pm_project_product_line";
    static final List<String> COLUMNS=DppmsProjectProductScopeApi.ORIGINAL_COLUMNS;
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("项目产品来源连接无效");
        var fields=new ArrayList<>(COLUMNS);fields.addAll(List.of("originalProjectCode","projectType"));
        var mappings=new ArrayList<>(fields.stream().map(name->SyncDefinition.Mapping.builder()
                .source(name).target(name).conversion("DIRECT").build()).toList());
        // Optional approved audit baseline links earlier PLT imports; it carries no product business values.
        mappings.add(SyncDefinition.Mapping.builder().source("id").target("baseline").conversion("LOOKUP").values(Map.of()).build());
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).sourceKey("id").readMode("SQL")
                .sql(DppmsMigrationSql.initial("project-products")).parameters(Map.of("projectTypes","10","projectIds",""))
                .columns(List.of()).filters(List.of()).mappings(mappings).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false).maxRows(10000)
                .readStrategy("STREAMING_CURSOR").fetchSize(1000).chunkSize(200).restartPolicy("RESTART_ALL")
                .queryTimeoutSeconds(0).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
