package cn.iocoder.yudao.module.pms.integration.api.sync;

import java.util.List;

/** INT-owned source identity reference, never a replacement for formal project business data. */
public interface DppmsProjectBindingApi {
    record Query(Long tenantId,Long sourceConnectionId,String sourceProjectKey) {}
    record BatchQuery(Long tenantId,Long sourceConnectionId,List<String> sourceProjectKeys,boolean originalOnly) {
        public BatchQuery(Long tenantId,Long sourceConnectionId,List<String> sourceProjectKeys) {this(tenantId,sourceConnectionId,sourceProjectKeys,false);}
        public BatchQuery originalLine() {return new BatchQuery(tenantId,sourceConnectionId,sourceProjectKeys,true);}
    }
    record ProjectBinding(Long sourceConnectionId,String sourceProjectKey,Long projectId,
                          String projectType,String originalProjectCode,String projectCode) {}
    List<ProjectBinding> resolve(BatchQuery query);
    default ProjectBinding resolve(Query query) {
        if(query==null || query.sourceProjectKey()==null) throw new IllegalArgumentException("DPPMS_PROJECT_BINDING_QUERY_INVALID");
        var rows=resolve(new BatchQuery(query.tenantId(),query.sourceConnectionId(),List.of(query.sourceProjectKey())));
        return rows.isEmpty()?null:rows.getFirst();
    }
}
