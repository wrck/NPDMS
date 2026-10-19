package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncBindingMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.SyncQueries;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor
public class DppmsProjectBindingApiImpl implements DppmsProjectBindingApi {
    private final SyncBindingMapper bindings;
    @Override public List<ProjectBinding> resolve(BatchQuery query) {
        if(query==null || !Objects.equals(query.tenantId(),TenantContextHolder.getRequiredTenantId())
                || query.sourceConnectionId()==null || query.sourceConnectionId()<=0
                || query.sourceProjectKeys()==null || query.sourceProjectKeys().size()>500
                || query.sourceProjectKeys().stream().anyMatch(key->key==null || !key.matches("[1-9][0-9]*"))
                || new HashSet<>(query.sourceProjectKeys()).size()!=query.sourceProjectKeys().size())
            throw new IllegalArgumentException("DPPMS_PROJECT_BINDING_QUERY_INVALID");
        if(query.sourceProjectKeys().isEmpty()) return List.of();
        Map<String,ProjectBinding> result=new LinkedHashMap<>();
        for(var row:bindings.selectIdentitySources(new SyncQueries.IdentitySources(query.tenantId(),"DPPMS",
                query.sourceConnectionId(),"DPPMS_PROJECT_MASTER","pm_project_header",query.sourceProjectKeys()).forLine(query.originalOnly()))) {
            if(!query.sourceProjectKeys().contains(row.getSourceKey()))throw new IllegalArgumentException("DPPMS_PROJECT_BINDING_EVIDENCE_INVALID");
            add(result,masterBinding(row,query.tenantId(),query.sourceConnectionId()));
        }
        var aliases=new ArrayList<SyncBindingDO>();
        for(String adapter:List.of(DppmsProjectMasterSyncTemplate.ADAPTER,DppmsProjectIdentitySyncTemplate.ADAPTER))
            aliases.addAll(bindings.selectIdentitySources(new SyncQueries.IdentitySources(query.tenantId(),"DPPMS",query.sourceConnectionId(),
                    adapter,"pm_project",query.sourceProjectKeys()).forLine(query.originalOnly())));
        var canonicalKeys=new LinkedHashSet<String>();
        for(var row:aliases){
            var saved=row.getFieldsJson()==null?null:JsonUtils.parseMap(row.getFieldsJson());
            if(saved==null || !(saved.get("originalIdentity") instanceof Map<?,?> original)
                    || !original.keySet().containsAll(DppmsProjectIdentitySyncTemplate.COLUMNS)
                    || !query.sourceProjectKeys().contains(row.getSourceKey())
                    || !Objects.equals(row.getSourceKey(),text(original.get("originalProjectId")))
                    || text(original.get("canonicalProjectId"))==null
                    || !text(original.get("canonicalProjectId")).matches("[1-9][0-9]*")
                    || Long.parseLong(row.getSourceKey())>Long.parseLong(text(original.get("canonicalProjectId"))))
                throw new IllegalArgumentException("DPPMS_PROJECT_ALIAS_EVIDENCE_INVALID");
            canonicalKeys.add(text(original.get("canonicalProjectId")));
        }
        if(!canonicalKeys.isEmpty()){
            var canonical=new LinkedHashMap<String,ProjectBinding>();
            for(var row:bindings.selectIdentitySources(new SyncQueries.IdentitySources(query.tenantId(),"DPPMS",query.sourceConnectionId(),
                    DppmsProjectMasterSyncTemplate.ADAPTER,DppmsProjectMasterSyncTemplate.OBJECT,List.copyOf(canonicalKeys)).forLine(query.originalOnly())))
                add(canonical,masterBinding(row,query.tenantId(),query.sourceConnectionId()));
            if(!canonical.keySet().equals(canonicalKeys))throw new IllegalArgumentException("DPPMS_PROJECT_ALIAS_MASTER_MISSING");
            for(var row:aliases){
                var saved=JsonUtils.parseMap(row.getFieldsJson());var original=(Map<?,?>)saved.get("originalIdentity");
                var target=canonical.get(text(original.get("canonicalProjectId")));
                if(!(saved.get("project") instanceof Map<?,?> project) || !Objects.equals(row.getTargetId(),target.projectId())
                        || !target.projectId().toString().equals(text(project.get("id")))
                        || !query.tenantId().toString().equals(text(project.get("tenantId")))
                        || !Objects.equals(target.projectType(),text(original.get("projectType")))
                        || !Objects.equals(target.originalProjectCode(),text(original.get("projectCode")))
                        || !Objects.equals(target.projectType(),text(project.get("projectType")))
                        || !Objects.equals(target.projectCode(),text(project.get("projectCode"))))
                    throw new IllegalArgumentException("DPPMS_PROJECT_ALIAS_TARGET_INVALID");
                add(result,new ProjectBinding(query.sourceConnectionId(),row.getSourceKey(),target.projectId(),
                        target.projectType(),target.originalProjectCode(),target.projectCode()));
            }
        }
        return List.copyOf(result.values());
    }
    static ProjectBinding masterBinding(SyncBindingDO row,long tenant,Long connection){
        var saved=row.getFieldsJson()==null?null:JsonUtils.parseMap(row.getFieldsJson());
        if(saved==null || !(saved.get("originalFields") instanceof Map<?,?> original)
                || !original.keySet().containsAll(DppmsProjectMasterSyncTemplate.ORIGINAL_COLUMNS)
                || !(saved.get("project") instanceof Map<?,?> project)
                || !Objects.equals(row.getSourceKey(),text(original.get("projectId")))
                || row.getTargetId()==null || row.getTargetId()<=0
                || !row.getTargetId().toString().equals(text(project.get("id")))
                || !Long.toString(tenant).equals(text(project.get("tenantId")))
                || text(original.get("projectCode"))==null || text(project.get("projectCode"))==null
                || !Set.of("10","afss","afxx").contains(text(original.get("projectType")))
                || !Objects.equals(text(original.get("projectType")),text(project.get("projectType"))))
            throw new IllegalArgumentException("DPPMS_PROJECT_BINDING_EVIDENCE_INVALID");
        return new ProjectBinding(connection,row.getSourceKey(),row.getTargetId(),text(original.get("projectType")),
                text(original.get("projectCode")),text(project.get("projectCode")));
    }
    private static void add(Map<String,ProjectBinding> result,ProjectBinding current){
        var previous=result.putIfAbsent(current.sourceProjectKey(),current);
        if(previous!=null && !previous.equals(current))throw new IllegalArgumentException("DPPMS_PROJECT_BINDING_AMBIGUOUS");
    }
    private static String text(Object value) {
        return value==null || value instanceof tools.jackson.databind.JsonNode node && node.isNull()?null:value.toString();
    }
}
