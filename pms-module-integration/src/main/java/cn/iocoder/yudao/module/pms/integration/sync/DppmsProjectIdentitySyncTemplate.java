package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Whole-table identity aliases; project facts are owned by the preceding master task. */
public final class DppmsProjectIdentitySyncTemplate {
    private DppmsProjectIdentitySyncTemplate() {}
    static final String ADAPTER="DPPMS_PROJECT_IDENTITY";
    static final String OBJECT="pm_project_identity";
    static final List<String> COLUMNS=List.of("originalProjectId","projectType","projectCode","canonicalProjectId");
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("项目来源连接无效");
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject("pm_project").readMode("SQL")
                .sql(DppmsMigrationSql.initial("project-identities")).parameters(Map.of("projectTypes","10","projectIds",""))
                .sourceKey("originalProjectId").columns(COLUMNS).filters(List.of()).mappings(COLUMNS.stream().map(c->
                        SyncDefinition.Mapping.builder().target(c).source(c).conversion("STRING").build()).toList()).build();
        return SyncDefinition.builder().connectionId(connectionId).sourceSystem("DPPMS").adapter(ADAPTER)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .retryCount(0).retryIntervalSeconds(60).resetMappingsBeforeLoad(false).clearBeforeLoad(false).autoPaging(false)
                .readStrategy("STREAMING_CURSOR").fetchSize(1000).chunkSize(500).restartPolicy("RESTART_ALL")
                .queryTimeoutSeconds(0).maxRows(10000).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
