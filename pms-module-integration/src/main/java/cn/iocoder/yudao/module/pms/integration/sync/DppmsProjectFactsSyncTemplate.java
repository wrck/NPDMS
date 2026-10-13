package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.project.api.migration.ProjectFactsMigrationApi;
import java.util.*;

public final class DppmsProjectFactsSyncTemplate {
    private DppmsProjectFactsSyncTemplate(){}
    static final String ADAPTER="DPPMS_PROJECT_FACTS",PARTY="pm_project_related_party",STATE="pm_project_state";
    public static SyncDefinition create(Long connectionId,Long definitionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("项目事实来源连接无效");
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).readStrategy("STREAMING_CURSOR")
                .fetchSize(1000).chunkSize(500).maxRows(500).maxBytes(8L*1024*1024)
                .sources(List.of(source(false,definitionId),source(true,definitionId))).build();
    }
    private static SyncDefinition.Source source(boolean state,Long definitionId) {
        var columns=new ArrayList<>(state?ProjectFactsMigrationApi.STATE_COLUMNS:ProjectFactsMigrationApi.PARTY_COLUMNS);
        columns.addAll(List.of("originalProjectCode","projectType"));
        var mappings=new ArrayList<>(columns.stream().map(name->SyncDefinition.Mapping.builder().source(name)
                .target(name).conversion("DIRECT").build()).toList());
        if(state)mappings.add(SyncDefinition.Mapping.builder().target("_definitionId").conversion("CONSTANT").constant(definitionId).build());
        else mappings.add(SyncDefinition.Mapping.builder().source("id").target("_expectedFingerprint").conversion("LOOKUP").values(Map.of()).build());
        return SyncDefinition.Source.builder().object(state?STATE:PARTY).sourceObject(state?STATE:PARTY)
                .sourceKey(state?"projectId":"id").readMode("SQL")
                .sql(DppmsMigrationSql.initial(state?"project-status-attributes":"project-related-parties"))
                .parameters(Map.of("projectTypes","10","projectIds","")).columns(List.of()).filters(List.of()).mappings(mappings).build();
    }
}
