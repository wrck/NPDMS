package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi.ProjectBinding;
import java.util.*;

/** Matches source relations locally without sending the published identity set to MySQL. */
final class PublishedProjectScopeRows {
    private record Code(String type, String code) {}
    private final String mode;
    private final Map<String,ProjectBinding> identities=new HashMap<>();
    private final Map<Code,ProjectBinding> codes=new HashMap<>();
    private final Set<Code> ambiguous=new HashSet<>();
    private final Set<String> types;
    private final Set<String> ids;

    static boolean requested(SyncDefinition.Source source) { return source.publishedProjectMatch()!=null; }

    static void validate(SyncDefinition definition) {
        if(definition.sources().stream().noneMatch(PublishedProjectScopeRows::requested))return;
        if(!"DPPMS".equals(definition.sourceSystem()) || "KEYSET_PAGING".equals(definition.effectiveReadStrategy())
                || !"RESTART_ALL".equals(definition.effectiveRestartPolicy()) || "INCREMENTAL".equals(definition.mode()))
            throw new IllegalArgumentException("已发布项目范围须完整读取且从头重试");
        for(var source:definition.sources()) {
            if(!requested(source))continue;
            if(!Set.of("ID","CANONICAL_ID","CODE").contains(source.publishedProjectMatch())
                    || !Set.of("SQL","TABLE").contains(source.readMode()) || SyncSourceProjection.reference(source))
                throw new IllegalArgumentException("已发布项目范围匹配配置无效");
        }
    }

    PublishedProjectScopeRows(SyncDefinition.Source source) {
        mode=source.publishedProjectMatch();
        var parameters=source.parameters()==null?Map.<String,Object>of():source.parameters();
        types=selection(parameters.get("projectTypes"));ids=selection(parameters.get("projectIds"));
        if(mode==null)return;
        if(!Set.of("ID","CANONICAL_ID","CODE").contains(mode))throw new IllegalArgumentException("项目范围匹配方式无效");
        if(source.publishedProjects()==null)throw new IllegalStateException("DPPMS_PROJECT_SCOPE_NOT_PUBLISHED");
        var canonical=new HashMap<Long,ProjectBinding>();
        for(var project:source.publishedProjects()) {
            if(project.sourceProjectKey()==null || !project.sourceProjectKey().matches("[1-9][0-9]*")
                    || project.projectId()==null || project.originalProjectCode()==null)
                throw new IllegalArgumentException("DPPMS_PROJECT_SCOPE_EVIDENCE_INVALID");
            var previous=identities.putIfAbsent(project.sourceProjectKey(),project);
            if(previous!=null && !previous.equals(project))throw new IllegalArgumentException("DPPMS_PROJECT_SCOPE_IDENTITY_CONFLICT");
            canonical.merge(project.projectId(),project,(a,b)->Long.parseLong(a.sourceProjectKey())>Long.parseLong(b.sourceProjectKey())?a:b);
        }
        if(!"ID".equals(mode)) {
            identities.clear();
            for(var project:canonical.values()) {
                identities.put(project.sourceProjectKey(),project);
                var code=new Code(project.projectType(),project.originalProjectCode());
                var previous=codes.putIfAbsent(code,project);
                if(previous!=null && !previous.projectId().equals(project.projectId()))ambiguous.add(code);
            }
        }
    }

    Map<String,Object> apply(Map<String,Object> row) {
        if(mode==null)return row;
        ProjectBinding project;
        if("CODE".equals(mode)) {
            String raw=text(row.get(row.containsKey("member_projectCode")?"member_projectCode":"originalProjectCode"));
            var key=new Code(text(row.get(row.containsKey("group_projectType")?"group_projectType":"projectType")),raw==null?null:raw.replace("-his",""));
            if(ambiguous.contains(key))throw new IllegalArgumentException("DPPMS_PROJECT_SCOPE_CODE_AMBIGUOUS");
            project=codes.get(key);
        } else project=identities.get(text(row.get("projectId")));
        if(project==null || !types.isEmpty() && !types.contains(project.projectType())
                || !ids.isEmpty() && !ids.contains(project.sourceProjectKey()))return null;
        var result=new LinkedHashMap<>(row);
        result.put("originalProjectCode",project.originalProjectCode());
        if("CODE".equals(mode)) {
            result.put("originalProjectId",project.sourceProjectKey());
            result.put("projectCandidateCount",1);
            result.put("canonical_originalProjectId",project.sourceProjectKey());
            result.put("canonical_originalProjectCode",project.originalProjectCode());
        } else result.put("projectType",project.projectType());
        return result;
    }

    private static Set<String> selection(Object value) {
        String text=text(value);
        return text==null || text.isBlank()?Set.of():Set.copyOf(Arrays.asList(text.split(",")));
    }
    private static String text(Object value) { return value==null?null:value.toString(); }
}
