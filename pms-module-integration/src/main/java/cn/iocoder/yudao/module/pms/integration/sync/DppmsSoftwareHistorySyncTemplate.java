package cn.iocoder.yudao.module.pms.integration.sync;
import java.util.*;
public final class DppmsSoftwareHistorySyncTemplate {
    static final String ADAPTER="DPPMS_SOFTWARE_HISTORY",BATCH="SOFTWARE_BATCH",VERSION="SOFTWARE_VERSION";
    static final List<String> BATCH_COLUMNS=List.of("id","projectId","changeVersion","changeRemark","latest","createBy","createTime","updateBy","updateTime");
    static final List<String> VERSION_COLUMNS=List.of("id","projectId","logId","contractNo","itemCode","barCode","conp","conpType","conpSeries","conpMark","conpBak","conpChange","cpld","cpldBak","cpldChange","boot","bootBak","bootChange","pcb","pcbBak","pcbChange","executeTime","datastate","createTime","createBy","updateTime","updateBy","customInfo");
    public record Request(Long connectionId,Long extensionDefinitionId) {}
    public static SyncDefinition create(Request request) {
        if(request==null || request.connectionId()==null || request.connectionId()<=0 || request.extensionDefinitionId()!=null && request.extensionDefinitionId()<=0)
            throw new IllegalArgumentException("软件历史来源连接或扩展定义无效");
        var root=source(BATCH).toBuilder().sql(DppmsMigrationSql.initial("software-history-combined")).groupKey("_groupKey")
            .rowProjection("_sourceRows").publishedProjectMatch("ID").parameters(Map.of("extensionDefinitionId",request.extensionDefinitionId()==null?"":request.extensionDefinitionId().toString())).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(request.connectionId()).mode("ONCE")
            .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
            .retryCount(0).retryIntervalSeconds(60).readStrategy("STREAMING_CURSOR").restartPolicy("RESTART_ALL")
            .fetchSize(25).chunkSize(25).maxRows(10000).maxBytes(64L*1024*1024)
            .sources(List.of(root,source(VERSION).toBuilder().queryRef(BATCH).build())).build();
    }
    static void validateDefinition(SyncDefinition definition) {
        if(definition.sources()==null || definition.sources().isEmpty())throw new IllegalArgumentException("软件历史缺少来源");
        var parameters=definition.sources().getFirst().parameters();
        String selected=Objects.toString(parameters==null?null:parameters.get("extensionDefinitionId"),"");
        Long id=selected.isBlank()?null:SyncFieldMapper.primaryKey(selected);
        if(!Objects.equals(definition.sources(),create(new Request(definition.connectionId(),id)).sources()))
            throw new IllegalArgumentException("软件历史须使用批准的父子原来源和本地项目范围");
    }
    static List<String> columns(String object) {
        var columns=new ArrayList<>(BATCH.equals(object)?BATCH_COLUMNS:VERSION_COLUMNS);columns.add("projectType");columns.add("originalProjectCode");return List.copyOf(columns);
    }
    private static SyncDefinition.Source source(String object) {
        return SyncDefinition.Source.builder().object(object).sourceObject(BATCH.equals(object)?"pm_project_soft_change_logs":"pm_project_soft_version")
            .readMode("SQL").sourceKey("id").columns(List.of()).filters(List.of()).parameters(Map.of())
            .mappings(columns(object).stream().map(n->SyncDefinition.Mapping.builder().source(n).target(n).conversion("DIRECT").build()).toList()).build();
    }
    private DppmsSoftwareHistorySyncTemplate() {}
}
