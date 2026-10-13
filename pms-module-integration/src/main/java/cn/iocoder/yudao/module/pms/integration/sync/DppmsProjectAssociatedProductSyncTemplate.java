package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.project.api.migration.ProjectAssociatedProductMigrationApi.Kind;
import java.util.*;

public final class DppmsProjectAssociatedProductSyncTemplate {
    private DppmsProjectAssociatedProductSyncTemplate() {}
    static final String ADAPTER="DPPMS_PROJECT_ASSOCIATED_PRODUCTS";
    static List<String> columns(Kind kind) {
        var names=new ArrayList<>(kind.columns);names.addAll(List.of("sourceProjectId","originalProjectCode","projectType"));return List.copyOf(names);
    }
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("项目附加产品来源连接无效");
        var sources=Arrays.stream(Kind.values()).map(kind->{
            var keyColumns=new ArrayList<>(kind.columns);keyColumns.add("sourceProjectId");
            return SyncDefinition.Source.builder().object(kind.sourceTable).sourceObject(kind.sourceTable).sourceKey("rowKey")
                    .sourceKeyColumns(List.copyOf(keyColumns)).readMode("SQL")
                    .sql(DppmsMigrationSql.initial(kind==Kind.LEASE_PRODUCT?"project-lease-products":"project-product-configurations"))
                    .parameters(Map.of("projectTypes","10","projectIds","")).columns(List.of()).filters(List.of())
                    .mappings(columns(kind).stream().map(name->SyncDefinition.Mapping.builder().source(name).target(name).conversion("DIRECT").build()).toList()).build();
        }).toList();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).readStrategy("SNAPSHOT").autoPaging(false)
                .maxRows(10000).maxBytes(64L*1024*1024).sources(sources).build();
    }
}
