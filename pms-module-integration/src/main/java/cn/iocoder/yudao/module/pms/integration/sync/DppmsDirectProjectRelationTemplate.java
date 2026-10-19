package cn.iocoder.yudao.module.pms.integration.sync;

import java.util.*;

/** Read complete contract and bridge originals; unresolved joins are retained and block import. */
public final class DppmsDirectProjectRelationTemplate {
    private DppmsDirectProjectRelationTemplate() {}
    static final String ADAPTER="DPPMS_PROJECT_CONTRACT_RELATION_DIRECT", OBJECT="pm_project_contract";
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
        if(connectionId==null || connectionId<=0)throw new IllegalArgumentException("项目合同来源连接无效");
        try(var input=new org.springframework.core.io.ClassPathResource("migration/dppms/tasks/P18-direct.json").getInputStream()) {
            return cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),SyncDefinition.class).toBuilder().connectionId(connectionId).build();
        }catch(java.io.IOException error){throw new IllegalStateException("项目合同直写定义缺失",error);}
    }
}
