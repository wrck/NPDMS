package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMemberSourceHistoryApi;
import java.util.*;
public final class DppmsMemberHistorySyncTemplate {
    static final String ADAPTER="DPPMS_MEMBER_HISTORY",MEMBERS="MEMBER_HISTORY",CONTACTS="CONTACT_HISTORY",PROJECTION="memberHistoryBranches";
    static List<String> columns(){var fields=new ArrayList<>(ProjectMemberSourceHistoryApi.COLUMNS);fields.add("originalProjectType");fields.add("originalProjectCode");return List.copyOf(fields);}
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("成员来源连接无效");
        var root=source(MEMBERS).toBuilder().sql("SELECT m.*,m.projectType AS originalProjectType FROM pm_project_member m")
                .groupKey("id").rowProjection(PROJECTION).publishedProjectMatch("ID").build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
            .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
            .retryCount(0).retryIntervalSeconds(60).readStrategy("STREAMING_CURSOR").restartPolicy("RESTART_ALL")
            .chunkSize(200).fetchSize(200).maxRows(200).maxBytes(64L*1024*1024)
            .sources(List.of(root,source(CONTACTS).toBuilder().queryRef(MEMBERS).build())).build();
    }
    private static SyncDefinition.Source source(String object) {
        return SyncDefinition.Source.builder().object(object).sourceObject("pm_project_member").readMode("SQL").sourceKey("id")
            .parameters(Map.of()).columns(List.of()).filters(List.of()).mappings(columns().stream().map(n->SyncDefinition.Mapping.builder()
                .source(n).target(n).conversion("DIRECT").build()).toList()).build();
    }
    private DppmsMemberHistorySyncTemplate() {}
}
