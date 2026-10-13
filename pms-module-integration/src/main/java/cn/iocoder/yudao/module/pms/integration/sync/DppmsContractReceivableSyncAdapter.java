package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractReceivableMigrationApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsContractReceivableSyncTemplate.*;

@Component @RequiredArgsConstructor
public class DppmsContractReceivableSyncAdapter implements DataSyncAdapter {
    private final ContractReceivableMigrationApi receipts;
    private final SystemUserMigrationApi users;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public void refreshCaches(){}
    @Override public Descriptor descriptor() {
        return new Descriptor(ADAPTER,"DPPMS合同回款信息",List.of(new ObjectDescriptor(OBJECT,"合同回款信息",
                ContractReceivableMigrationApi.ORIGINAL_COLUMNS.stream().map(name->new Field(name,name,"STRING",false)).toList(),
                "COM","ContractReceivable","com_contract_receivable",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch==null || batch.owner()==null || !batch.owner().matches("integration:"+tenant+":[1-9][0-9]*")
                || batch.clearBeforeLoad() || batch.adoptExisting() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy()))fail("运行范围无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(batch.owner().substring(batch.owner().lastIndexOf(':')+1)));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || definition==null
                || definition.resetMappingsBeforeLoad() || run==null || !Objects.equals(run.getTenantId(),tenant)
                || !Objects.equals(run.getTaskId(),task.getId()) || !Objects.equals(run.getConfigVersion(),task.getVersion())
                || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))fail("配置或运行已变化");
        var prior=new HashMap<String,Binding>();var names=new HashSet<String>();
        for(var binding:batch.bindings())if(!OBJECT.equals(binding.object()) || prior.put(binding.sourceKey(),binding)!=null)fail("重复绑定");
        var keys=new HashSet<String>();
        for(var row:batch.rows()) {
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(ContractReceivableMigrationApi.ORIGINAL_COLUMNS)
                    || !Objects.equals(row.sourceKey(),text(row.fields().get("id"))) || !keys.add(row.sourceKey()))fail("原字段或身份不完整");
            var binding=prior.get(row.sourceKey());
            if(binding==null && row.targetId()!=null || binding!=null && (!Objects.equals(binding.targetId(),row.targetId())
                    || text(binding.lastFields().get("fingerprint"))==null))fail("绑定变化");
            for(String name:List.of("create_by","update_by"))if(text(row.fields().get(name))!=null)names.add(text(row.fields().get(name)));
        }
        var identities=new HashMap<String,String>();
        for(var user:users.getHistoricalIdentitiesByUsername(tenant,names))
            if(identities.put(user.username(),user.id().toString())!=null)fail("同名审计账号多义");
        var entries=new ArrayList<ContractReceivableMigrationApi.Entry>();
        for(var row:batch.rows()) {
            var original=new LinkedHashMap<String,String>();ContractReceivableMigrationApi.ORIGINAL_COLUMNS.forEach(name->original.put(name,text(row.fields().get(name))));
            var binding=prior.get(row.sourceKey());
            entries.add(new ContractReceivableMigrationApi.Entry(row.sourceKey(),Collections.unmodifiableMap(original),
                    identities.getOrDefault(original.get("create_by"),original.get("create_by")),
                    identities.getOrDefault(original.get("update_by"),original.get("update_by")),row.targetId(),
                    binding==null?null:text(binding.lastFields().get("fingerprint"))));
        }
        var request=new ContractReceivableMigrationApi.Request(tenant,SyncFieldMapper.primaryKey(run.getCreator()),batch.owner(),List.copyOf(entries));
        var results=apply?receipts.apply(request):receipts.preview(request);
        if(results.size()!=entries.size())fail("Owner回执不完整");
        var changes=new ArrayList<Change>();var returned=new HashSet<String>();
        for(var result:results) {
            if(!keys.contains(result.sourceKey()) || !returned.add(result.sourceKey())
                    || !Set.of("CREATED","UPDATED","UNCHANGED","SKIPPED").contains(result.action()))fail("Owner回执身份无效");
            var binding=prior.get(result.sourceKey());var after=new LinkedHashMap<String,Object>();
            if(result.fingerprint()!=null){after.put("receivable",result.fields());after.put("fingerprint",result.fingerprint());}
            changes.add(new Change(OBJECT,result.sourceKey(),result.targetId(),result.action(),binding==null?Map.of():binding.lastFields(),after,
                    "SKIPPED".equals(result.action())?"按裁决过滤空合同号":"39原字段复用正式回款信息；不生成收款或验收完成动作"));
        }
        return List.copyOf(changes);
    }
    private static String text(Object value){return value==null || value instanceof tools.jackson.databind.JsonNode n && n.isNull()?null:value.toString();}
    private static void fail(String message){throw new IllegalArgumentException("DPPMS合同回款信息："+message);}
}
