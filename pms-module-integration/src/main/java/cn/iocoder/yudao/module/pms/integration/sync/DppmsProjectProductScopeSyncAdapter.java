package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsProjectProductScopeApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectProductScopeSyncTemplate.*;

/** Source SQL selects whole-table originals; COM alone owns scope fields, quantities and allocation effects. */
@Component @RequiredArgsConstructor
public class DppmsProjectProductScopeSyncAdapter implements DataSyncAdapter {
    private final DppmsProjectProductScopeApi scopes;
    private final DppmsProjectBindingApi projectBindings;
    private final ProjectMasterMigrationApi projects;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    @Override public boolean supportsStreaming() { return true; }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public Descriptor descriptor() {
        var names=new ArrayList<>(COLUMNS);names.addAll(List.of("originalProjectCode","projectType","baseline"));
        return new Descriptor(ADAPTER,"DPPMS项目产品实施范围",List.of(new ObjectDescriptor(OBJECT,"项目产品",
                names.stream().map(name->new Field(name,name,"STRING",false)).toList(),"COM","DeliveryScope",
                "com_delivery_scope",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override public void refreshCaches() {}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch) { return execute(batch,false); }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch) { return execute(batch,true); }
    private List<Change> execute(Batch batch,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch==null || batch.clearBeforeLoad() || batch.adoptExisting() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy()) || batch.owner()==null || !batch.owner().matches("integration:"+tenant+":[1-9][0-9]*"))
            fail("运行范围无效，禁止清空、删除或接管");
        var task=tasks.required(SyncFieldMapper.primaryKey(batch.owner().substring(batch.owner().lastIndexOf(':')+1)));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!Objects.equals(task.getTenantId(),tenant) || !ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem())
                || definition==null || !"ONCE".equals(definition.mode()) || definition.resetMappingsBeforeLoad()
                || run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus())) fail("运行及配置版本失效");
        var previous=new HashMap<String,Binding>();
        for(var binding:batch.bindings()) if(!OBJECT.equals(binding.object()) || previous.put(binding.sourceKey(),binding)!=null) fail("来源绑定重复");
        var sourceRows=new LinkedHashMap<String,Row>();var rawRows=new HashMap<String,Map<String,String>>();
        var projectKeys=new LinkedHashSet<String>();
        for(var row:batch.rows()) {
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(COLUMNS)
                    || sourceRows.put(row.sourceKey(),row)!=null || !row.sourceKey().equals(text(row.fields().get("id")))) fail("全部原字段或来源身份无效");
            SyncFieldMapper.primaryKey(row.sourceKey());String project=text(row.fields().get("projectId"));SyncFieldMapper.primaryKey(project);projectKeys.add(project);
            var raw=new LinkedHashMap<String,String>();COLUMNS.forEach(name->raw.put(name,text(row.fields().get(name))));rawRows.put(row.sourceKey(),raw);
            var prior=previous.get(row.sourceKey());
            if(prior==null && row.targetId()!=null || prior!=null && (!Objects.equals(row.targetId(),prior.targetId())
                    || !(prior.lastFields().get("originalFields") instanceof Map<?,?> saved)
                    || !saved.keySet().equals(raw.keySet()) || raw.keySet().stream().anyMatch(name->!Objects.equals(raw.get(name),text(saved.get(name))))))
                fail("原字段或绑定发生变化，禁止自动接管");
        }
        var references=projectBindings.resolve(new DppmsProjectBindingApi.BatchQuery(tenant,task.getConnectionId(),List.copyOf(projectKeys)));
        var byProject=new HashMap<String,DppmsProjectBindingApi.ProjectBinding>();
        for(var ref:references)if(byProject.put(ref.sourceProjectKey(),ref)!=null)fail("正式项目绑定多义");
        if(!byProject.keySet().equals(projectKeys))fail("先完成正式项目身份，禁止按名称猜配");
        for(var row:sourceRows.values()) {
            var ref=byProject.get(text(row.fields().get("projectId")));
            if(!Objects.equals(ref.originalProjectCode(),text(row.fields().get("originalProjectCode")))
                    || !Objects.equals(ref.projectType(),text(row.fields().get("projectType"))))fail("原项目类型、编码与绑定不一致");
        }
        long actor=SyncFieldMapper.primaryKey(run.getCreator());
        var facts=projects.inspectBindings(new ProjectMasterMigrationApi.IdentityInspection(tenant,actor,references.stream()
                .map(ref->new ProjectMasterMigrationApi.IdentityTarget(ref.projectId(),ref.projectType(),ref.projectCode())).distinct().toList()));
        var byTarget=new HashMap<Long,ProjectMasterMigrationApi.Facts>();facts.forEach(fact->byTarget.put(fact.id(),fact));
        if(!byTarget.keySet().equals(new HashSet<>(references.stream().map(DppmsProjectBindingApi.ProjectBinding::projectId).toList())))fail("正式项目回执缺失");
        if(apply)projects.lockBindings(new ProjectMasterMigrationApi.BindingVerification(tenant,actor,facts));
        var grouped=new LinkedHashMap<String,List<Entry>>();
        for(var row:sourceRows.values()) {
            var prior=previous.get(row.sourceKey());Object saved=prior==null?row.fields().get("baseline"):prior.lastFields().get("baseline");
            var audit=text(saved)==null?null:JsonUtils.parseMap(saved instanceof String value?value:JsonUtils.toJsonString(saved));
            var baseline=audit==null?null:new Baseline(SyncFieldMapper.primaryKey(text(audit.get("id"))),text(audit.get("creator")),
                    SyncFieldMapper.sourceTime(audit.get("createTime")),text(audit.get("updater")),SyncFieldMapper.sourceTime(audit.get("updateTime")),
                    text(audit.get("formalFieldsJson")));
            if(prior!=null && baseline!=null) {
                Object scopeBefore=prior.lastFields().get("scope");
                if(!(scopeBefore instanceof Map<?,?>))fail("完整正式范围前值缺失");
                baseline=new Baseline(baseline.id(),baseline.creator(),baseline.createTime(),baseline.updater(),baseline.updateTime(),formalFields((Map<?,?>)scopeBefore));
            }
            if(prior!=null && (baseline==null || !Objects.equals(prior.targetId(),baseline.id())))fail("正式范围前值缺失");
            grouped.computeIfAbsent(text(row.fields().get("projectId")),key->new ArrayList<>()).add(new Entry(row.sourceKey(),rawRows.get(row.sourceKey()),baseline));
        }
        var changes=new HashMap<String,Change>();
        for(var group:grouped.entrySet()) {
            var ref=byProject.get(group.getKey());var fact=byTarget.get(ref.projectId());
            var command=new Command(tenant,actor,ref.projectId(),fact.version(),group.getKey(),List.copyOf(group.getValue()),task.getConnectionId());
            var results=apply?scopes.apply(command):scopes.preview(command);
            if(results.size()!=group.getValue().size())fail("COM回执数量不一致");
            for(var result:results) {
                var original=rawRows.get(result.sourceKey());var row=sourceRows.get(result.sourceKey());
                if(original==null || !group.getKey().equals(original.get("projectId")) || changes.containsKey(result.sourceKey()))fail("COM来源回执身份不一致");
                var fields=result.fields();var baseline=new Baseline(result.targetId(),text(fields.get("creator")),SyncFieldMapper.sourceTime(fields.get("createTime")),
                        text(fields.get("updater")),SyncFieldMapper.sourceTime(fields.get("updateTime")));
                var prior=previous.get(result.sourceKey());
                var kept=new LinkedHashMap<String,Object>();original.forEach((name,value)->kept.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value));
                var formal=new LinkedHashMap<String,Object>();fields.forEach((name,value)->formal.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value instanceof LocalDateTime time?time.toString():value));
                var after=new LinkedHashMap<String,Object>();after.put("scope",formal);after.put("baseline",baseline);after.put("originalFields",kept);
                changes.put(result.sourceKey(),new Change(OBJECT,result.sourceKey(),result.targetId(),result.action(),
                        prior==null?Map.of():prior.lastFields(),after,"正式实施范围；项目数量优先、订单数量补缺；不占用当前分配数量"));
            }
        }
        return batch.rows().stream().map(row->changes.get(row.sourceKey())).toList();
    }
    private static String formalFields(Map<?,?> fields) {
        var result=new LinkedHashMap<String,Object>();fields.forEach((name,value)->{
            if(!Set.of("productDetail","productDetailAction").contains(name.toString()))
                result.put(name.toString(),value==null?tools.jackson.databind.node.NullNode.getInstance():value instanceof LocalDateTime time?time.toString():value);
        });
        return JsonUtils.toJsonString(result);
    }
    private static String text(Object value) { return value==null || value instanceof tools.jackson.databind.JsonNode node && node.isNull()?null:value.toString(); }
    private static void fail(String message) { throw new IllegalArgumentException("DPPMS实施范围迁移："+message); }
}
