package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.api.sync.DppmsProjectBindingApi;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectFactsSyncTemplate.*;

@Component @RequiredArgsConstructor
public class DppmsProjectFactsSyncAdapter implements DataSyncAdapter {
    private final ProjectFactsMigrationApi facts;
    private final DppmsProjectBindingApi bindings;
    private final ProjectMasterMigrationApi projects;
    private final SystemUserMigrationApi users;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public boolean sharesTargetAcrossSources(){return true;}
    @Override public void refreshCaches(){}
    @Override public Descriptor descriptor() {
        return new Descriptor(ADAPTER,"DPPMS项目相关方及状态",List.of(object(PARTY,"项目相关方",ProjectFactsMigrationApi.PARTY_COLUMNS,"ProjectParty","proj_project_party"),
                object(STATE,"项目发货与执行状态",ProjectFactsMigrationApi.STATE_COLUMNS,"project","proj_project")),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    private static ObjectDescriptor object(String object,String label,List<String> names,String entity,String table) {
        var columns=new ArrayList<>(names);columns.addAll(List.of("originalProjectCode","projectType","_definitionId","_expectedFingerprint"));
        return new ObjectDescriptor(object,label,columns.stream().map(name->new Field(name,name,"STRING",false)).toList(),"PROJ",entity,table,false);
    }
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch==null || batch.owner()==null || !batch.owner().matches("integration:"+tenant+":[1-9][0-9]*")
                || batch.adoptExisting() || batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy()))fail("运行范围无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(batch.owner().substring(batch.owner().lastIndexOf(':')+1)));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || definition==null || definition.resetMappingsBeforeLoad()
                || run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))fail("配置或运行已变化");
        if(batch.rows().isEmpty())return List.of();
        var prior=new HashMap<String,Binding>();var originals=new LinkedHashMap<String,Row>();var sourceProjects=new HashSet<String>();var names=new HashSet<String>();
        for(var binding:batch.bindings())if(prior.put(binding.object()+":"+binding.sourceKey(),binding)!=null)fail("绑定重复");
        for(var row:batch.rows()) {
            var columns=PARTY.equals(row.object())?ProjectFactsMigrationApi.PARTY_COLUMNS:STATE.equals(row.object())?ProjectFactsMigrationApi.STATE_COLUMNS:null;
            String key=row.object()+":"+row.sourceKey();
            if(columns==null || !row.fields().keySet().containsAll(columns) || originals.put(key,row)!=null
                    || !Objects.equals(row.sourceKey(),text(row.fields().get(PARTY.equals(row.object())?"id":"projectId"))))fail("原字段或来源身份缺失");
            var binding=prior.get(key);
            if(binding==null && row.targetId()!=null || binding!=null && (!Objects.equals(binding.targetId(),row.targetId())
                    || text(binding.lastFields().get("fingerprint"))==null))fail("绑定目标或前值变化");
            sourceProjects.add(text(row.fields().get("projectId")));
            if(PARTY.equals(row.object()))for(String name:List.of("createBy","updateBy"))if(text(row.fields().get(name))!=null)names.add(text(row.fields().get(name)));
        }
        var refs=new HashMap<String,DppmsProjectBindingApi.ProjectBinding>();
        for(var ref:bindings.resolve(new DppmsProjectBindingApi.BatchQuery(tenant,task.getConnectionId(),List.copyOf(sourceProjects))))
            if(refs.put(ref.sourceProjectKey(),ref)!=null)fail("项目绑定多义");
        if(!sourceProjects.containsAll(refs.keySet()))fail("返回了范围外项目绑定");
        var changes=new ArrayList<Change>();
        originals.entrySet().removeIf(entry->{
            var row=entry.getValue();
            if(refs.containsKey(text(row.fields().get("projectId"))))return false;
            if(row.targetId()!=null || prior.containsKey(entry.getKey()))fail("现存资料的项目绑定缺失，禁止接管");
            changes.add(new Change(row.object(),row.sourceKey(),null,"ISSUE",Map.of(),row.fields(),
                    "PROJECT_BINDING_MISSING：先迁入规范项目主档"));return true;
        });
        if(originals.isEmpty())return List.copyOf(changes);
        for(var row:originals.values()) {
            var ref=refs.get(text(row.fields().get("projectId")));
            if(!Objects.equals(ref.originalProjectCode(),text(row.fields().get("originalProjectCode")))
                    || !Objects.equals(ref.projectType(),text(row.fields().get("projectType"))))fail("项目原编码或类型不同");
        }
        long actor=SyncFieldMapper.primaryKey(run.getCreator());
        var targetFacts=projects.inspectBindings(new ProjectMasterMigrationApi.IdentityInspection(tenant,actor,refs.values().stream()
                .map(ref->new ProjectMasterMigrationApi.IdentityTarget(ref.projectId(),ref.projectType(),ref.projectCode())).distinct().toList()));
        if(apply)projects.lockBindings(new ProjectMasterMigrationApi.BindingVerification(tenant,actor,targetFacts));
        var versions=new HashMap<Long,Long>();targetFacts.forEach(fact->versions.put(fact.id(),fact.version()));
        var actors=new HashMap<String,String>();
        var requestedNames=new ArrayList<>(names);
        for(int offset=0;offset<requestedNames.size();offset+=200)
            for(var user:users.getHistoricalIdentitiesByUsername(tenant,new HashSet<>(requestedNames.subList(offset,Math.min(offset+200,requestedNames.size())))))
                if(!names.contains(user.username()) || actors.put(user.username(),user.id().toString())!=null)fail("审计账号同名多义");
        var grouped=new LinkedHashMap<String,List<ProjectFactsMigrationApi.Entry>>();Long revision=null;
        for(var row:originals.values()) {
            var original=new LinkedHashMap<String,String>();(PARTY.equals(row.object())?ProjectFactsMigrationApi.PARTY_COLUMNS:ProjectFactsMigrationApi.STATE_COLUMNS)
                    .forEach(name->original.put(name,text(row.fields().get(name))));
            var binding=prior.get(row.object()+":"+row.sourceKey());
            String baseline=binding==null?text(row.fields().get("_expectedFingerprint")):text(binding.lastFields().get("fingerprint"));
            if(STATE.equals(row.object())) {
                Long selected=SyncFieldMapper.primaryKey(text(row.fields().get("_definitionId")));
                if(revision!=null && !revision.equals(selected))fail("同批扩展定义不同");revision=selected;
            }
            grouped.computeIfAbsent(original.get("projectId"),key->new ArrayList<>()).add(new ProjectFactsMigrationApi.Entry(row.object(),row.sourceKey(),
                    Collections.unmodifiableMap(original),actors.getOrDefault(original.get("createBy"),original.get("createBy")),
                    actors.getOrDefault(original.get("updateBy"),original.get("updateBy")),row.targetId(),baseline));
        }
        var returned=new HashSet<String>();
        for(var group:grouped.entrySet()) {
            var ref=refs.get(group.getKey());var command=new ProjectFactsMigrationApi.Command(new ProjectMigrationIdentityContextApi.Query(
                    tenant,actor,ref.projectId(),versions.get(ref.projectId()),group.getKey(),task.getConnectionId()),revision,List.copyOf(group.getValue()));
            var results=apply?facts.apply(command):facts.preview(command);
            if(results.size()!=group.getValue().size())fail("Owner回执数量不同");
            for(var result:results) {
                String key=result.object()+":"+result.sourceKey();var row=originals.get(key);var binding=prior.get(key);
                if(row==null || !returned.add(key) || !group.getKey().equals(text(row.fields().get("projectId")))
                        || !Set.of("CREATED","UPDATED","UNCHANGED").contains(result.action()))fail("Owner回执身份不同");
                changes.add(new Change(result.object(),result.sourceKey(),result.targetId(),result.action(),binding==null?Map.of():binding.lastFields(),
                        Map.of("fields",result.fields(),"fingerprint",result.fingerprint()),"正式项目相关方及状态属性；不迁入审批或工程完成事实"));
            }
        }
        return List.copyOf(changes);
    }
    private static String text(Object value){return value==null || value instanceof tools.jackson.databind.JsonNode n && n.isNull()?null:value.toString();}
    private static void fail(String message){throw new IllegalArgumentException("DPPMS项目资料："+message);}
}
