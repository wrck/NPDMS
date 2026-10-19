package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectIdentitySyncTemplate.*;

/** Tracks every original ID after canonical masters are migrated; never writes project facts. */
@RequiredArgsConstructor
final class DirectProjectIdentities implements DataSyncAdapter {
    record RestorationProof(Long retiredBindingId,Long lastRunId,Long targetId) {}
    private final DirectProjectPersistence projects;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public boolean sharesTargetAcrossSources(){return true;}
    @Override public boolean restoresSourceBindings(){return true;}
    @Override public void refreshCaches(){}
    @Override public Descriptor descriptor(){return new Descriptor(ADAPTER,"DPPMS整表项目身份映射",
            List.of(new ObjectDescriptor(OBJECT,"全部原项目ID到规范主档",java.util.stream.Stream.concat(
                    COLUMNS.stream().map(c->new Field(c,c,"STRING",true)),java.util.stream.Stream.of(
                    new Field("migrationRestoration","获准恢复的原身份绑定","OBJECT",false))).toList(),
                    "PROJ","Project","proj_project",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,readOnly=true)
    public List<Change> preview(Batch batch){return execute(batch,false,null);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true,null);}
    record Consolidated(Batch batch, boolean apply, List<Change> masters) {}
    List<Change> consolidated(Consolidated input) {
        return execute(input.batch(), input.apply(), Objects.requireNonNull(input.masters()));
    }
    private List<Change> execute(Batch batch,boolean apply,List<Change> masters){
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch==null || batch.clearBeforeLoad() || batch.adoptExisting() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy()) || masters==null && batch.rows().size()>500)fail("身份任务禁止清空、接管或重绑");
        String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))fail("运行所有权无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        String expectedAdapter=masters==null?ADAPTER:DppmsDirectProjectTemplate.ADAPTER;
        if(!Objects.equals(task.getTenantId(),tenant) || !expectedAdapter.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem())
                || definition==null || !"ONCE".equals(definition.mode()) || definition.resetMappingsBeforeLoad()
                || run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))fail("运行或配置版本失效");
        var previous=new HashMap<String,Binding>();
        for(var binding:batch.bindings())if(!OBJECT.equals(binding.object()) || previous.put(binding.sourceKey(),binding)!=null)fail("原身份绑定重复");
        var restorationKeys=batch.rows().stream().filter(r->r.fields().get("migrationRestoration")!=null && !previous.containsKey(r.sourceKey())).map(Row::sourceKey).toList();
        var retired=new HashMap<String,cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO>();
        if(!restorationKeys.isEmpty())for(var binding:bindings.selectRetiredSourceKeysForUpdate(
                new SyncQueries.SourceKeys(tenant,task.getId(),OBJECT,"pm_project",restorationKeys)))
            if(retired.put(binding.getSourceKey(),binding)!=null)fail("原身份存在重复撤回绑定");
        var identities=new LinkedHashMap<String,Map<String,String>>();var canonicalKeys=new LinkedHashSet<String>();
        for(var row:batch.rows()){
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(COLUMNS) || row.fields().containsKey("_sourcePrimaryKey"))fail("原身份字段缺失");
            var identity=new LinkedHashMap<String,String>();for(String c:COLUMNS)identity.put(c,text(row.fields().get(c)));
            if(!Objects.equals(row.sourceKey(),identity.get("originalProjectId")) || identities.put(row.sourceKey(),identity)!=null
                    || !Set.of("10","afss","afxx").contains(identity.get("projectType")) || identity.get("projectCode")==null)fail("原身份或类型无效");
            long original=SyncFieldMapper.primaryKey(identity.get("originalProjectId")),canonical=SyncFieldMapper.primaryKey(identity.get("canonicalProjectId"));
            if(original>canonical)fail("规范主档必须采用同类型同编码最大ID");
            canonicalKeys.add(identity.get("canonicalProjectId"));
        }
        var canonical=new LinkedHashMap<String,Map<String,Object>>();
        if(masters!=null) {
            for(var master:masters) {
                if(!DppmsProjectMasterSyncTemplate.OBJECT.equals(master.object()) || !canonicalKeys.contains(master.sourceKey()))
                    fail("合并身份只能引用本块规范主档");
                if(Set.of("CREATED","UPDATED","UNCHANGED").contains(master.action()))
                    if(canonical.put(master.sourceKey(),master.after())!=null)fail("本块规范主档重复");
            }
        } else if(!canonicalKeys.isEmpty())for(var binding:bindings.selectIdentitySources(new SyncQueries.IdentitySources(tenant,"DPPMS",task.getConnectionId(),
                DppmsDirectProjectTemplate.ADAPTER,DppmsProjectMasterSyncTemplate.OBJECT,List.copyOf(canonicalKeys)))){
            DppmsProjectBindingApiImpl.masterBinding(binding,tenant,task.getConnectionId());
            var saved=JsonUtils.parseMap(binding.getFieldsJson());var prior=canonical.putIfAbsent(binding.getSourceKey(),saved);
            if(prior!=null && !JsonUtils.parseTree(JsonUtils.toJsonString(prior)).equals(JsonUtils.parseTree(binding.getFieldsJson())))fail("规范主档绑定存在差异");
        }
        if(!canonicalKeys.containsAll(canonical.keySet()))fail("返回了范围外规范主档");
        var targets=new LinkedHashMap<Long,ProjectMasterMigrationApi.IdentityTarget>();var changes=new ArrayList<Change>();
        for(var row:batch.rows()){
            var identity=identities.get(row.sourceKey());var saved=canonical.get(identity.get("canonicalProjectId"));
            if(saved==null) {
                if(row.targetId()!=null || previous.containsKey(row.sourceKey()))fail("已绑定原身份的规范主档缺失，禁止自动重绑");
                changes.add(new Change(OBJECT,row.sourceKey(),null,"ISSUE",Map.of(),row.fields(),
                        "PROJECT_BINDING_MISSING：先迁入规范项目 "+identity.get("canonicalProjectId")));
                continue;
            }
            var original=(Map<?,?>)saved.get("originalFields");var project=(Map<?,?>)saved.get("project");
            if(!Objects.equals(identity.get("projectType"),text(original.get("projectType")))
                    || !Objects.equals(identity.get("projectCode"),text(original.get("projectCode"))))fail("原项目类型或编码与规范主档不一致");
            if(masters!=null && !apply && text(project.get("id"))==null) {
                if(row.targetId()!=null || previous.containsKey(row.sourceKey()))fail("已绑定原身份不能重新创建规范主档");
                changes.add(new Change(OBJECT,row.sourceKey(),null,"UNCHANGED",Map.of(),
                        Map.of("originalIdentity",identity),"同一事务创建规范项目后保存原ID映射"));
                continue;
            }
            Long targetId=SyncFieldMapper.primaryKey(text(project.get("id")));
            var target=new ProjectMasterMigrationApi.IdentityTarget(targetId,text(project.get("projectType")),text(project.get("projectCode")));
            var priorTarget=targets.putIfAbsent(targetId,target);
            if(priorTarget!=null && !priorTarget.equals(target))fail("正式主档身份多义");
            var projectIdentity=Map.of("id",targetId,"tenantId",tenant,"projectType",target.projectType(),"projectCode",target.projectCode());
            var after=Map.<String,Object>of("originalIdentity",identity,"project",projectIdentity);
            var prior=previous.get(row.sourceKey());
            if(prior==null && row.fields().get("migrationRestoration")!=null) {
                var proof=JsonUtils.parseObject(JsonUtils.toJsonString(row.fields().get("migrationRestoration")),RestorationProof.class);
                var old=retired.get(row.sourceKey());
                if(proof==null || old==null || !Objects.equals(proof.retiredBindingId(),old.getId())
                        || !Objects.equals(proof.targetId(),targetId) || !Objects.equals(old.getTargetId(),targetId)
                        || !Objects.equals(proof.lastRunId(),old.getLastRunId())
                        || !JsonUtils.parseTree(old.getFieldsJson()).equals(JsonUtils.parseTree(JsonUtils.toJsonString(after))))
                    fail("获准恢复的原身份、目标或原值不匹配");
                var receipt=runs.selectScoped(new SyncQueries.Id(tenant,proof.lastRunId()));
                if(receipt==null || !Objects.equals(receipt.getTaskId(),task.getId()) || !Objects.equals(receipt.getTenantId(),tenant)
                        || !Boolean.FALSE.equals(receipt.getPreview()) || !"SUCCESS".equals(receipt.getStatus()))
                    fail("原身份恢复缺少成功回执");
            }
            if(row.targetId()!=null && prior==null || prior!=null && (!Objects.equals(prior.targetId(),row.targetId())
                    || !Objects.equals(prior.targetId(),targetId)
                    || !JsonUtils.parseTree(JsonUtils.toJsonString(prior.lastFields())).equals(JsonUtils.parseTree(JsonUtils.toJsonString(after)))))
                fail("已绑定原身份或正式项目发生变化，禁止自动重绑");
            changes.add(new Change(OBJECT,row.sourceKey(),targetId,"UNCHANGED",prior==null?Map.of():prior.lastFields(),after,
                    "原ID关联规范正式主档；不覆盖业务字段、不产生授权"));
        }
        if(targets.isEmpty())return List.copyOf(changes);
        Long actor=SyncFieldMapper.primaryKey(run.getCreator());
        var current=projects.inspectBindings(new ProjectMasterMigrationApi.IdentityInspection(tenant,actor,List.copyOf(targets.values())));
        if(current.size()!=targets.size() || !current.stream().map(ProjectMasterMigrationApi.Facts::id).collect(java.util.stream.Collectors.toSet()).equals(targets.keySet()))
            fail("正式Owner返回的当前项目范围不完整");
        var verification=new ProjectMasterMigrationApi.BindingVerification(tenant,actor,current);
        if(apply)projects.lockBindings(verification);else projects.verifyBindings(verification);
        if(apply)for(var change:changes) {
            var old=retired.get(change.sourceKey());
            if(old!=null && change.targetId()!=null && "UNCHANGED".equals(change.action()) && bindings.restoreVerifiedBinding(old)!=1)
                fail("原身份来源绑定恢复冲突，当前块回滚");
        }
        return List.copyOf(changes);
    }
    private static String text(Object v){return v==null || v instanceof tools.jackson.databind.JsonNode n && n.isNull()?null:v.toString();}
    private static void fail(String message){throw new IllegalArgumentException(message);}
}
