package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectRealProductSyncTemplate.*;

/** The editable source query feeds the existing PROJ product-list Owner. */
@Component @RequiredArgsConstructor
public class DppmsProjectRealProductSyncAdapter implements DataSyncAdapter {
    private final ProjectRealProductMigrationApi products;
    private final DppmsProjectBindingApi bindings;
    private final ProjectMasterMigrationApi projects;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    @Override public Descriptor descriptor() {
        return new Descriptor(ADAPTER,"DPPMS项目真实产品清单",List.of(new ObjectDescriptor(OBJECT,"真实产品清单",
                columns().stream().map(name->new Field(name,name,"STRING",false)).toList(),"PROJ","ProjectRealProduct",
                "proj_project_real_product",false)),List.of("RETAIN"),List.of("UPSERT"),false,false);
    }
    @Override public void refreshCaches() {}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch) { return execute(batch,false); }
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch) { return execute(batch,true); }
    private List<Change> execute(Batch batch,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch==null || batch.clearBeforeLoad() || batch.adoptExisting() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy()) || batch.owner()==null || !batch.owner().matches("integration:"+tenant+":[1-9][0-9]*"))fail("运行范围无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(batch.owner().substring(batch.owner().lastIndexOf(':')+1)));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!Objects.equals(task.getTenantId(),tenant) || !ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem())
                || definition==null || !"ONCE".equals(definition.mode()) || definition.resetMappingsBeforeLoad()
                || run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))fail("运行或配置失效");
        if(batch.rows().isEmpty())return List.of();
        var previous=new HashMap<String,Binding>();
        for(var prior:batch.bindings())if(!OBJECT.equals(prior.object()) || previous.put(prior.sourceKey(),prior)!=null)fail("绑定重复");
        var originals=new LinkedHashMap<String,Row>();var sourceKeys=new LinkedHashSet<String>();
        for(var row:batch.rows()) {
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(columns()) || originals.put(row.sourceKey(),row)!=null
                    || !row.sourceKey().equals(text(row.fields().get("id"))))fail("来源身份或字段不完整");
            SyncFieldMapper.primaryKey(row.sourceKey());var project=text(row.fields().get("projectId"));SyncFieldMapper.primaryKey(project);sourceKeys.add(project);
            var prior=previous.get(row.sourceKey());
            if(prior==null && row.targetId()!=null || prior!=null && (!Objects.equals(row.targetId(),prior.targetId())
                    || !originalsMatch(row,prior)))fail("来源或绑定变化，禁止接管");
        }
        var references=new HashMap<String,DppmsProjectBindingApi.ProjectBinding>();var keys=new ArrayList<>(sourceKeys);
        for(int offset=0;offset<keys.size();offset+=500)
            for(var ref:bindings.resolve(new DppmsProjectBindingApi.BatchQuery(tenant,task.getConnectionId(),List.copyOf(keys.subList(offset,Math.min(offset+500,keys.size()))))))
                if(references.put(ref.sourceProjectKey(),ref)!=null)fail("项目身份多义");
        if(!references.keySet().equals(sourceKeys))fail("先完成正式项目绑定");
        for(var row:originals.values()) {
            var ref=references.get(text(row.fields().get("projectId")));
            if(!Objects.equals(ref.originalProjectCode(),text(row.fields().get("originalProjectCode")))
                    || !Objects.equals(ref.projectType(),text(row.fields().get("projectType"))))fail("项目类型或原编码不同");
        }
        long actor=SyncFieldMapper.primaryKey(run.getCreator());var targets=references.values().stream()
                .map(ref->new ProjectMasterMigrationApi.IdentityTarget(ref.projectId(),ref.projectType(),ref.projectCode())).distinct().toList();
        var facts=new HashMap<Long,ProjectMasterMigrationApi.Facts>();
        for(int offset=0;offset<targets.size();offset+=500) {
            var block=projects.inspectBindings(new ProjectMasterMigrationApi.IdentityInspection(tenant,actor,List.copyOf(targets.subList(offset,Math.min(offset+500,targets.size())))));
            if(apply)projects.lockBindings(new ProjectMasterMigrationApi.BindingVerification(tenant,actor,block));
            block.forEach(fact->facts.put(fact.id(),fact));
        }
        if(!facts.keySet().equals(new HashSet<>(targets.stream().map(ProjectMasterMigrationApi.IdentityTarget::projectId).toList())))fail("项目回执不完整");
        var grouped=new LinkedHashMap<String,List<ProjectRealProductMigrationApi.Source>>();
        for(var row:originals.values()) {
            var prior=previous.get(row.sourceKey());String baseline=prior==null?null:text(prior.lastFields().get("baseline"));
            if(prior!=null && baseline==null)fail("正式前值缺失");
            int count=Integer.parseInt(text(row.fields().get("upstreamMatchCount")));
            grouped.computeIfAbsent(text(row.fields().get("projectId")),key->new ArrayList<>()).add(new ProjectRealProductMigrationApi.Source(
                    row.sourceKey(),values(row,ProjectRealProductMigrationApi.ORIGINAL_COLUMNS,""),values(row,ProjectRealProductMigrationApi.UPSTREAM_COLUMNS,"upstream_"),count,row.targetId(),baseline));
        }
        var changes=new HashMap<String,Change>();
        for(var group:grouped.entrySet()) {
            var ref=references.get(group.getKey());var command=new ProjectRealProductMigrationApi.Command(new ProjectMigrationIdentityContextApi.Query(
                    tenant,actor,ref.projectId(),facts.get(ref.projectId()).version(),group.getKey(),task.getConnectionId()),List.copyOf(group.getValue()));
            var results=apply?products.apply(command):products.preview(command);
            if(results.size()!=group.getValue().size())fail("正式回执数量不一致");
            for(var result:results) {
                var row=originals.get(result.sourceKey());var prior=previous.get(result.sourceKey());
                if(row==null || !group.getKey().equals(text(row.fields().get("projectId"))) || changes.containsKey(result.sourceKey())
                        || !Set.of("CREATED","UNCHANGED").contains(result.action())
                        || prior!=null && !Objects.equals(prior.targetId(),result.targetId()))fail("正式回执身份不一致");
                var after=new LinkedHashMap<String,Object>();after.put("originalFields",kept(values(row,ProjectRealProductMigrationApi.ORIGINAL_COLUMNS,"")));
                after.put("upstreamFields",kept(values(row,ProjectRealProductMigrationApi.UPSTREAM_COLUMNS,"upstream_")));
                after.put("product",kept(result.fields()));after.put("baseline",result.baseline());
                changes.put(result.sourceKey(),new Change(OBJECT,result.sourceKey(),result.targetId(),result.action(),prior==null?Map.of():prior.lastFields(),after,"正式真实产品清单，原业务值完整承接"));
            }
        }
        return batch.rows().stream().map(row->changes.get(row.sourceKey())).toList();
    }
    private static Map<String,String> values(Row row,List<String> columns,String prefix) {
        var values=new LinkedHashMap<String,String>();columns.forEach(name->values.put(name,text(row.fields().get(prefix+name))));return Collections.unmodifiableMap(values);
    }
    private static boolean originalsMatch(Row row,Binding prior) {
        for(var entry:Map.of("originalFields",ProjectRealProductMigrationApi.ORIGINAL_COLUMNS,"upstreamFields",ProjectRealProductMigrationApi.UPSTREAM_COLUMNS).entrySet()) {
            if(!(prior.lastFields().get(entry.getKey()) instanceof Map<?,?> saved) || !saved.keySet().equals(new HashSet<>(entry.getValue())))return false;
            String prefix="upstreamFields".equals(entry.getKey())?"upstream_":"";
            if(entry.getValue().stream().anyMatch(name->!Objects.equals(text(saved.get(name)),text(row.fields().get(prefix+name)))))return false;
        }
        return true;
    }
    private static Map<String,Object> kept(Map<String,?> values) {
        var result=new LinkedHashMap<String,Object>();values.forEach((name,value)->result.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value instanceof LocalDateTime time?time.toString():value));return result;
    }
    private static String text(Object value) { return value==null || value instanceof tools.jackson.databind.JsonNode node && node.isNull()?null:value.toString(); }
    private static void fail(String message) { throw new IllegalArgumentException("DPPMS真实产品迁移："+message); }
}
