package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Read complete contract and bridge originals; unresolved joins are retained and block import. */
public final class DppmsProjectContractRelationSyncTemplate {
    private DppmsProjectContractRelationSyncTemplate() {}
    static final String ADAPTER="DPPMS_PROJECT_CONTRACT_RELATION", OBJECT="pm_project_contract";
    static final String DUPLICATE_GROUP_SIZE="duplicateGroupSize";
    static final List<String> STREAM_COLUMNS=List.of("canonicalOriginalId","canonicalCreatedAt","duplicateIdentical","projectCandidateCount");
    static final List<String> ORIGINAL_COLUMNS=List.of("id","contractNo","projectGroupCode","createTime","createBy","updateTime","updateBy");
    static final List<String> MEMBER_COLUMNS=List.of("id","projectGroupCode","projectCode","mergeBranchMark","smsProjectCode","createTime","createBy","updateTime","updateBy");
    static final List<String> GROUP_COLUMNS=List.of("id","projectGroupCode","projectGroupName","projectType","createTime","createBy","updateTime","updateBy");
    static List<String> columns() {
        var columns=new ArrayList<>(ORIGINAL_COLUMNS);
        MEMBER_COLUMNS.forEach(name->columns.add("member_"+name));GROUP_COLUMNS.forEach(name->columns.add("group_"+name));
        columns.add("originalProjectId");columns.add("originalProjectCode");return columns;
    }
    public static SyncDefinition create(Long connectionId) {
        if(connectionId==null || connectionId<=0) throw new IllegalArgumentException("项目合同来源连接无效");
        String sql=DppmsMigrationSql.initial("project-contract-formal-relations");
        var mappings=new ArrayList<>(columns().stream().map(name->SyncDefinition.Mapping.builder().source(name).target(name).conversion("DIRECT").build()).toList());
        DppmsProjectContractFormalDecision.columns().forEach(name->mappings.add(SyncDefinition.Mapping.builder().source(name).target(name).conversion("DIRECT").build()));
        for(String name:List.of("reviewedBefore","migrationDecision")) mappings.add(SyncDefinition.Mapping.builder()
                .source("relationOriginalKey").target(name).conversion("LOOKUP").values(Map.of()).build());
        var source=SyncDefinition.Source.builder().object(OBJECT).sourceObject(OBJECT).sourceKey("relationSourceKey")
                .readMode("SQL").sql(sql).parameters(Map.of("projectTypes","10","projectIds","")).columns(List.of()).filters(List.of()).mappings(mappings).build();
        return SyncDefinition.builder().adapter(ADAPTER).sourceSystem("DPPMS").connectionId(connectionId).mode("ONCE")
                .loadingMode("UPSERT").missingPolicy("RETAIN").cron("0 0 2 * * ?").fullCron("0 0 2 ? * SUN")
                .overlapSeconds(0).retryCount(0).retryIntervalSeconds(60).autoPaging(false).maxRows(10000)
                .readStrategy("STREAMING_CURSOR").fetchSize(1000).chunkSize(500).restartPolicy("RESTART_ALL").queryTimeoutSeconds(0)
                .maxBytes(64L*1024*1024).sources(List.of(source)).build();
    }
}
