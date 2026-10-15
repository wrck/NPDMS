package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.engineering.api.installation.DppmsInstallationRecordMigrationApi;
import java.util.*;

/** Initial disabled-task definition; no source-time sorting or historical mapping reset. */
public final class DppmsInstallationSyncTemplate {
    private DppmsInstallationSyncTemplate() {}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("安装历史来源连接无效");
        var columns=new ArrayList<>(DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS);
        columns.add("originalProjectCode");columns.add("projectType");
        var source=SyncDefinition.Source.builder().object(DppmsInstallationSyncAdapter.OBJECT).sourceObject("pm_project_shipment")
                .readMode("TABLE").table("pm_project_shipment").sourceKey("id").publishedProjectMatch("ID")
                .columns(DppmsInstallationRecordMigrationApi.ORIGINAL_COLUMNS).filters(List.of()).parameters(Map.of())
                .mappings(columns.stream().map(n->SyncDefinition.Mapping.builder().source(n).target(n).conversion("DIRECT").build()).toList()).build();
        return SyncDefinition.builder().adapter(DppmsInstallationSyncAdapter.ADAPTER).sourceSystem("DPPMS").connectionId(connectionId)
                .mode("ONCE").loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .retryCount(0).retryIntervalSeconds(60).readStrategy("STREAMING_CURSOR").restartPolicy("RESTART_ALL")
                .fetchSize(500).chunkSize(500).maxRows(500).maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
