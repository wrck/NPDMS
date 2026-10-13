package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.project.api.migration.ProjectRealProductMigrationApi;
import java.util.*;

public final class DppmsProjectRealProductSyncTemplate {
    private DppmsProjectRealProductSyncTemplate() {}
    static final String ADAPTER="DPPMS_PROJECT_REAL_PRODUCT",OBJECT="pm_project_product_line_real";
    static List<String> columns() {
        var names=new ArrayList<>(ProjectRealProductMigrationApi.ORIGINAL_COLUMNS);
        ProjectRealProductMigrationApi.UPSTREAM_COLUMNS.forEach(name->names.add("upstream_"+name));
        names.addAll(List.of("originalProjectCode","projectType","upstreamMatchCount"));return List.copyOf(names);
    }
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("真实产品来源连接无效");
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).sourceKey("id").readMode("SQL")
                .sql(DppmsMigrationSql.initial("project-real-products")).parameters(Map.of("projectTypes","10","projectIds",""))
                .columns(List.of()).filters(List.of()).mappings(columns().stream().map(name->SyncDefinition.Mapping.builder()
                        .source(name).target(name).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).readStrategy("SNAPSHOT").autoPaging(false)
                .maxRows(10000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
