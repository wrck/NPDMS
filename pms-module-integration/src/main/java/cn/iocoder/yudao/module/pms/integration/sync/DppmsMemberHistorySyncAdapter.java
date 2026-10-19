package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.integration.api.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsMemberHistorySyncTemplate.*;
@Component @RequiredArgsConstructor
public class DppmsMemberHistorySyncAdapter implements DataSyncAdapter {
    private final SyncOwnerRunContext context;
    private final DppmsPublishedProjectScopeApi published;
    private final ProjectMasterMigrationApi projects;
    private final ProjectMemberSourceHistoryApi owner;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public Descriptor descriptor() {
        var fields=columns().stream().map(n->new Field(n,n,"STRING",false)).toList();
        return new Descriptor(ADAPTER,"DPPMS成员与联系人历史",List.of(
            new ObjectDescriptor(MEMBERS,"成员历史",fields,"PROJ","ProjectMemberAssignment","proj_project_member_assignment",false),
            new ObjectDescriptor(CONTACTS,"最终客户联系人历史",fields,"CUS","ProjectCustomerContact","cus_project_customer_contact_relation",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override public void refreshCaches() {}
    @Override @Transactional(rollbackFor=Exception.class) public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class) public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        var run=context.require(new SyncOwnerRunContext.Request(batch,ADAPTER,apply));
        var prior=new HashMap<String,Binding>();for(var b:batch.bindings())if(prior.put(b.object()+":"+b.sourceKey(),b)!=null)fail("BINDING_DUPLICATE");
        var originals=new LinkedHashMap<String,Map<String,String>>();var ids=new LinkedHashSet<String>();
        for(var row:batch.rows()) {
            if(!Set.of(MEMBERS,CONTACTS).contains(row.object()) || !row.fields().keySet().containsAll(columns())
                    || CONTACTS.equals(row.object())!= "60".equals(text(row.fields().get("memberRole"))))fail("SOURCE_INVALID");
            var raw=new LinkedHashMap<String,String>();ProjectMemberSourceHistoryApi.COLUMNS.forEach(n->raw.put(n,text(row.fields().get("projectType".equals(n)?"originalProjectType":n))));
            String key=row.object()+":"+row.sourceKey();if(!Objects.equals(row.sourceKey(),raw.get("id")) || originals.put(key,raw)!=null)fail("SOURCE_INVALID");
            var old=prior.get(key);
            if(old==null && row.targetId()!=null || old!=null && (!Objects.equals(row.targetId(),old.targetId())
                    || !(old.lastFields().get("originalFields") instanceof Map<?,?> fields) || !fields.keySet().equals(raw.keySet())
                    || raw.keySet().stream().anyMatch(n->!Objects.equals(raw.get(n),text(fields.get(n))))))fail("BOUND_SOURCE_CHANGED");
            ids.add(raw.get("projectId"));
        }
        if(ids.isEmpty())return List.of();
        var scope=published.resolve(new DppmsPublishedProjectScopeApi.Query(run.tenantId(),run.task().getConnectionId(),List.copyOf(ids)));
        var references=new HashMap<String,DppmsProjectBindingApi.ProjectBinding>();
        for(var project:scope.projects())if(!ids.contains(project.sourceProjectKey()) || references.put(project.sourceProjectKey(),project)!=null)fail("PROJECT_AMBIGUOUS");
        if(!references.keySet().equals(ids))fail("PROJECT_SCOPE_CHANGED");
        var grouped=new TreeMap<Long,List<Row>>();
        for(var row:batch.rows()) {
            var raw=originals.get(row.object()+":"+row.sourceKey());var project=references.get(raw.get("projectId"));
            if(!Objects.equals(raw.get("projectType"),project.projectType()) || !Objects.equals(text(row.fields().get("projectType")),project.projectType())
                    || !Objects.equals(text(row.fields().get("originalProjectCode")),project.originalProjectCode()))fail("PROJECT_IDENTITY_CHANGED");
            grouped.computeIfAbsent(project.projectId(),k->new ArrayList<>()).add(row);
        }
        var changes=new HashMap<String,Change>();
        for(var group:grouped.entrySet()) {
            var first=group.getValue().getFirst();var reference=references.get(originals.get(first.object()+":"+first.sourceKey()).get("projectId"));
            var facts=projects.inspectBindings(new ProjectMasterMigrationApi.IdentityInspection(run.tenantId(),run.actorId(),
                List.of(new ProjectMasterMigrationApi.IdentityTarget(group.getKey(),reference.projectType(),reference.projectCode()))));
            if(facts.size()!=1 || !Objects.equals(facts.getFirst().id(),group.getKey()))fail("PROJECT_UNRESOLVED");
            projects.lockBindings(new ProjectMasterMigrationApi.BindingVerification(run.tenantId(),run.actorId(),facts));
            var entries=group.getValue().stream().map(row->{var old=prior.get(row.object()+":"+row.sourceKey());
                if(old!=null && !Objects.equals(text(old.lastFields().get("projectId")),group.getKey().toString()))fail("BOUND_TARGET_CHANGED");
                return new ProjectMemberSourceHistoryApi.Entry(row.sourceKey(),originals.get(row.object()+":"+row.sourceKey()),row.targetId(),old==null?null:text(old.lastFields().get("formalFieldsJson")));}).toList();
            var command=new ProjectMemberSourceHistoryApi.Command(run.tenantId(),run.actorId(),group.getKey(),entries);
            var receipts=apply?owner.apply(command):owner.preview(command);var byKey=new HashMap<String,ProjectMemberSourceHistoryApi.Result>();
            for(var receipt:receipts)if(byKey.put(receipt.sourceKey(),receipt)!=null)fail("OWNER_DUPLICATE");
            if(byKey.size()!=entries.size())fail("OWNER_INCOMPLETE");
            for(var row:group.getValue()) {
                var receipt=byKey.get(row.sourceKey());var old=prior.get(row.object()+":"+row.sourceKey());
                String table=CONTACTS.equals(row.object())?"cus_project_customer_contact_relation":"proj_project_member_assignment";
                if(receipt==null || !table.equals(receipt.targetTable()) || apply&&(receipt.targetId()==null || receipt.targetId()<=0)
                        || old!=null && !Objects.equals(old.targetId(),receipt.targetId()))fail("OWNER_INVALID");
                var raw=originals.get(row.object()+":"+row.sourceKey());
                String json=raw.entrySet().stream().map(e->JsonUtils.toJsonString(e.getKey())+":"+(e.getValue()==null?"null":JsonUtils.toJsonString(e.getValue())))
                    .collect(java.util.stream.Collectors.joining(",","{","}"));
                var after=new LinkedHashMap<String,Object>();after.put("originalFields",JsonUtils.parseTree(json));after.put("projectId",group.getKey());after.put("formalFieldsJson",receipt.formalFieldsJson());
                changes.put(row.object()+":"+row.sourceKey(),new Change(row.object(),row.sourceKey(),receipt.targetId(),receipt.action(),old==null?Map.of():old.lastFields(),after,"历史承接，不授当前权限"));
            }
        }
        return batch.rows().stream().map(row->changes.get(row.object()+":"+row.sourceKey())).toList();
    }
    private static String text(Object v){return v==null || v instanceof tools.jackson.databind.JsonNode n&&n.isNull()?null:v.toString();}
    private static void fail(String code){throw new IllegalArgumentException("DPPMS_MEMBER_"+code);}
}
