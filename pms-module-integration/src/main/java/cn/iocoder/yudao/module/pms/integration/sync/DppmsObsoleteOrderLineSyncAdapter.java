package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.OrderLineMigrationPresenceApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsObsoleteOrderLineSyncTemplate.*;

/** Only removes approved obsolete raw-01 bindings with missing targets; it never deletes a business row or old run. */
@Component @RequiredArgsConstructor
public class DppmsObsoleteOrderLineSyncAdapter implements DataSyncAdapter {
    private final SyncTaskService tasks;
    private final SyncTaskMapper taskMapper;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    private final OrderLineMigrationPresenceApi lines;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public void refreshCaches(){}
    @Override public Descriptor descriptor(){return new Descriptor(ADAPTER,"清理旧01订单行迁移绑定",
            List.of(new ObjectDescriptor(OBJECT,"已裁决旧01绑定",List.of(new Field("id","原ID","STRING",true),
                    new Field("compCode","原公司码","STRING",true),new Field("_originalTaskId","原迁移任务","STRING",true)),
                    "INT","SyncBinding","int_sync_binding",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);}
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
        if(!ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || definition==null || !"ONCE".equals(definition.mode())
                || definition.resetMappingsBeforeLoad() || run==null || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getConfigVersion(),task.getVersion())
                || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))fail("配置或运行已变化");
        if(batch.rows().isEmpty())return List.of();
        Long original=null;var sourceKeys=new LinkedHashSet<String>();
        for(var row:batch.rows()) {
            Long selected=SyncFieldMapper.primaryKey(Objects.toString(row.fields().get("_originalTaskId"),null));
            if(!OBJECT.equals(row.object()) || !"01".equals(Objects.toString(row.fields().get("compCode"),null))
                    || !Objects.equals(row.sourceKey(),Objects.toString(row.fields().get("id"),null))
                    || row.targetId()!=null || !sourceKeys.add(row.sourceKey()) || original!=null && !original.equals(selected))fail("只能清理指定原任务的01记录");
            original=selected;
        }
        var oldTask=taskMapper.selectForUpdate(new SyncQueries.Id(tenant,original));
        if(oldTask==null || Boolean.TRUE.equals(oldTask.getDeleted()) || oldTask.getActiveRunId()!=null
                || !"DPPMS_ERP_ORDER".equals(oldTask.getAdapter()) || !"DPPMS".equals(oldTask.getSourceSystem())
                || !Objects.equals(oldTask.getConnectionId(),task.getConnectionId()))fail("原任务不存在、正在运行或来源不同");
        var old=bindings.selectObsoleteOrderLinesForUpdate(new SyncQueries.ObsoleteOrderLines(tenant,original,List.copyOf(sourceKeys)));
        var existing=lines.existingTargets(new OrderLineMigrationPresenceApi.Query(tenant,SyncFieldMapper.primaryKey(run.getCreator()),
                old.stream().map(b->b.getTargetId()).distinct().toList()));
        if(!existing.isEmpty())fail("旧01绑定仍有正式目标，禁止删除");
        if(apply && !old.isEmpty() && bindings.deleteObsoleteOrderLines(new SyncQueries.ObsoleteOrderLineDelete(tenant,original,
                old.stream().map(b->b.getId()).toList()))!=old.size())fail("绑定删除数量变化");
        var deleted=new HashMap<String,cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO>();
        for(var binding:old)if(deleted.put(binding.getSourceKey(),binding)!=null)fail("原绑定身份重复");
        return batch.rows().stream().map(row->{
            var binding=deleted.get(row.sourceKey());var before=binding==null?Map.<String,Object>of():Map.<String,Object>of(
                    "bindingId",binding.getId(),"originalTaskId",binding.getTaskId(),"targetId",binding.getTargetId(),"companyCode","01");
            return new Change(OBJECT,row.sourceKey(),null,binding==null?"SKIPPED":"DELETED",before,Map.of(),
                    binding==null?"无待删除旧01绑定；映射001和其他公司绑定保持":"按裁决删除旧01无目标迁移绑定；旧运行回执及正式数据保持");
        }).toList();
    }
    private static void fail(String message){throw new IllegalArgumentException("旧01绑定清理："+message);}
}
