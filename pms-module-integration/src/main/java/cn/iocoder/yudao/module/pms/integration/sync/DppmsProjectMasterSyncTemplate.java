package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import java.util.*;

/** Complete header migration; explicit expected versions are supplied after reviewing existing targets. */
public final class DppmsProjectMasterSyncTemplate {
    private DppmsProjectMasterSyncTemplate() {}
    static final String ADAPTER="DPPMS_PROJECT_MASTER";
    static final String OBJECT="pm_project_header";
    static final List<String> ORIGINAL_COLUMNS=ProjectMasterMigrationApi.ORIGINAL_COLUMNS;
    static final List<String> ACTOR_COLUMNS=DppmsContractCollectionPlanSyncTemplate.ACTOR_COLUMNS;
    static final List<String> ACTOR_REFERENCE_COLUMNS=List.of("creatorFndSourceKeys","updaterFndSourceKeys");
    static final Set<String> DATES=Set.of("column009","createTime","updateTime","effectiveFrom","effectiveTo",
            "projectStartTime","projectRefreshTime","projectCloseTime");
    public static SyncDefinition create(Long connectionId) {
        if (connectionId==null || connectionId<=0) throw new IllegalArgumentException("项目来源连接无效");
        var columns=new ArrayList<>(ORIGINAL_COLUMNS); columns.addAll(ACTOR_COLUMNS); columns.addAll(ACTOR_REFERENCE_COLUMNS);columns.add("migrationCompanyCode");
        var mappings=new ArrayList<SyncDefinition.Mapping>();
        for (String column:columns) mappings.add(SyncDefinition.Mapping.builder().target(column).source(column)
                .conversion(DATES.contains(column)?"DATETIME":"disabled".equals(column)?"BOOLEAN":"STRING").build());
        mappings.add(SyncDefinition.Mapping.builder().target("expectedProjectVersion").source("projectId")
                .conversion("LOOKUP").values(Map.of()).build());
        mappings.add(SyncDefinition.Mapping.builder().target("migrationCodeAllocation").source("projectId")
                .conversion("LOOKUP").values(Map.of()).build());
        for(String kind:List.of("creator","updater")) mappings.add(SyncDefinition.Mapping.builder().target(kind+"HistoricalCode")
                .source("creator".equals(kind)?"createBy":"updateBy").conversion("LOOKUP").values(Map.of("sys","sys","jiangyating","jiangyating")).build());
        String sql=DppmsMigrationSql.initial("project-master");
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT)
                .readMode("SQL").sql(sql).parameters(Map.of("projectTypes","10","projectIds","","excludedFndActorIds","")).sourceKey("projectId").updatedAt(null)
                .columns(columns).filters(List.of()).mappings(mappings).build();
        return SyncDefinition.builder().connectionId(connectionId).sourceSystem("DPPMS").adapter(ADAPTER)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN")
                .cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN").retryCount(0).retryIntervalSeconds(60)
                .resetMappingsBeforeLoad(false).clearBeforeLoad(false).autoPaging(false)
                .readStrategy("STREAMING_CURSOR").fetchSize(1000).chunkSize(500).restartPolicy("RESTART_ALL")
                .queryTimeoutSeconds(0).maxRows(10000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
