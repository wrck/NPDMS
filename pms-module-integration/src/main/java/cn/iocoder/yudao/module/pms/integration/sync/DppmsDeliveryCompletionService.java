package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsContractCompletionApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncRunDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Only qualified local facts enter the Commerce Owner's once-per-run completion. */
@Service @RequiredArgsConstructor
public class DppmsDeliveryCompletionService {
    private final SyncTaskMapper tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    private final DppmsContractCompletionApi contracts;
    public record Completion(SyncRunDO run,SyncDefinition definition) {}
    static boolean eligible(SyncDefinition d) {
        return "TABLE_MAPPING".equals(d.adapter()) && "DPPMS".equals(d.sourceSystem())
                && "delivery-full-sync".equals(cn.iocoder.yudao.module.pms.integration.sync.GenericDirectTaskLine.businessKey(d)) && d.sources().stream().anyMatch(SyncLocalOwnerReader::reference);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public DppmsContractCompletionApi.Result complete(Completion completion) {
        var current=completion.run();var definition=completion.definition();
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(!eligible(definition) || !Objects.equals(tenant,current.getTenantId())
                || !Boolean.TRUE.equals(current.getFullSnapshot()) || current.getFinishedAt()!=null
                || !completeSummary(current,false))
            throw new IllegalArgumentException("P15后置汇总要求本次全部来源完整承接，不能使用失败或部分输入");
        var candidates=tasks.selectSourceAdapter(new SyncQueries.SourceAdapter(tenant,definition.connectionId(),"DPPMS",DppmsContractReceivableSyncTemplate.ADAPTER));
        if(candidates.size()!=1)throw new IllegalArgumentException("P15后置汇总缺少同连接唯一P24回款任务");
        var receivableTask=tasks.selectForUpdate(new SyncQueries.Id(tenant,candidates.getFirst().getId()));
        if(receivableTask==null || receivableTask.getActiveRunId()!=null)
            throw new IllegalArgumentException("P24回款输入正在变化，不能作为完整输入");
        var snapshot=runs.selectLatestFormalRoot(new SyncQueries.Task(tenant,receivableTask.getId()));
        if(snapshot==null || !"SUCCESS".equals(snapshot.getStatus()) || !Boolean.TRUE.equals(snapshot.getFullSnapshot())
                || !Objects.equals(snapshot.getConfigVersion(),receivableTask.getVersion()) || !completeSummary(snapshot,true)
                || !fullReceivableSource(snapshot,definition.connectionId()))
            throw new IllegalArgumentException("P24回款没有当前配置的完整成功快照，不能使用失败或部分输入");
        // Empty-contract P24 rows are an approved Owner filter, rather than missing input.
        var contractIds=ids(new SyncQueries.DeliveryBindings(tenant,current.getTaskId(),"CONTRACT","com_sales_order",current.getId()));
        var shipmentIds=ids(new SyncQueries.DeliveryBindings(tenant,current.getTaskId(),"SHIPMENT_CONTRACT","fb_contract",null));
        var receivableIds=ids(new SyncQueries.DeliveryBindings(tenant,receivableTask.getId(),DppmsContractReceivableSyncTemplate.OBJECT,DppmsContractReceivableSyncTemplate.OBJECT,null));
        var currentShipmentIds=ids(new SyncQueries.DeliveryBindings(tenant,current.getTaskId(),"SHIPMENT_CONTRACT","fb_contract",current.getId()));
        var currentPackageIds=ids(new SyncQueries.DeliveryBindings(tenant,current.getTaskId(),"PACKAGE","fb_shipment",current.getId()));
        var result=contracts.complete(new DppmsContractCompletionApi.Command(tenant,SyncFieldMapper.primaryKey(current.getCreator()),
                Boolean.TRUE.equals(current.getPreview()),new DppmsContractCompletionApi.DeliveryScope(contractIds,currentShipmentIds,currentPackageIds),shipmentIds,receivableIds));
        if(!Boolean.TRUE.equals(current.getPreview()))refreshProjections(current);
        return result;
    }
    private void refreshProjections(SyncRunDO run) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        for(var target:DppmsContractCompletionApi.Target.values()) {
            String sourceObject=switch(target){case CONTRACT->"com_sales_order";case SHIPMENT_CONTRACT->"fb_contract";case PACKAGE->"fb_shipment";};
            Long after=null;
            while(true) {
                var page=bindings.selectDeliveryProjectionPage(new SyncQueries.DeliveryProjectionBindings(tenant,run.getTaskId(),run.getId(),target.name(),sourceObject,after));
                if(page.isEmpty())break;
                var ids=new LinkedHashSet<Long>();
                for(var binding:page) {
                    if(binding.getId()==null || binding.getTargetId()==null || binding.getTargetId()<=0)
                        throw new IllegalArgumentException("后置投影绑定身份不完整");
                    ids.add(binding.getTargetId());
                }
                var projections=new HashMap<Long,DppmsContractCompletionApi.Projection>();
                for(var projection:contracts.readProjection(new DppmsContractCompletionApi.ProjectionQuery(tenant,SyncFieldMapper.primaryKey(run.getCreator()),target,ids)))
                    if(!ids.contains(projection.getId()) || projections.putIfAbsent(projection.getId(),projection)!=null)
                        throw new IllegalArgumentException("后置投影Owner回执越界或重复");
                if(projections.size()!=ids.size())throw new IllegalArgumentException("后置投影Owner回执不完整");
                for(var binding:page) {
                    var fields=new LinkedHashMap<>(SyncBindingSnapshots.decode(binding.getFieldsJson()));
                    fields.put("_postCompletion",JsonUtils.parseMap(JsonUtils.toJsonString(projections.get(binding.getTargetId()))));
                    binding.setFieldsJson(SyncBindingSnapshots.encode(fields,true));
                    if(bindings.updateById(binding)!=1)throw new IllegalStateException("后置投影绑定已变化");
                }
                Long last=page.getLast().getId();
                if(after!=null && last<=after)throw new IllegalStateException("后置投影游标未推进");
                after=last;
            }
        }
    }
    private Set<Long> ids(SyncQueries.DeliveryBindings query) {
        var ids=new LinkedHashSet<Long>();
        for(var binding:bindings.selectDeliveryInputs(query)) {
            if(binding.getTargetId()==null || binding.getTargetId()<=0)
                throw new IllegalArgumentException("后置汇总输入绑定未承接到正式Owner对象");
            ids.add(binding.getTargetId());
        }
        return Collections.unmodifiableSet(ids);
    }
    private static boolean fullReceivableSource(SyncRunDO snapshot,Long connectionId) {
        if(snapshot.getConfigSnapshot()==null)return false;
        var d=JsonUtils.parseObject(snapshot.getConfigSnapshot(),SyncDefinition.class);
        if(!DppmsContractReceivableSyncTemplate.ADAPTER.equals(d.adapter()) || !"DPPMS".equals(d.sourceSystem())
                || !Objects.equals(connectionId,d.connectionId()) || d.sources()==null || d.sources().size()!=1)return false;
        var source=d.sources().getFirst();var approved=DppmsContractReceivableSyncTemplate.create(connectionId).sources().getFirst();
        return Objects.equals(source.object(),approved.object()) && Objects.equals(source.sourceObject(),approved.sourceObject())
                && Objects.equals(source.sourceKey(),approved.sourceKey()) && "SQL".equals(source.readMode())
                && source.sql()!=null && source.sql().replace("\r\n","\n").strip().equals(approved.sql().replace("\r\n","\n").strip())
                && (source.parameters()==null || source.parameters().entrySet().stream().allMatch(e->"contractNos".equals(e.getKey())
                    && (e.getValue()==null || e.getValue().toString().isBlank())))
                && Objects.equals(source.updatedAt(),approved.updatedAt())
                && source.mappings()!=null && new HashSet<>(source.mappings()).equals(new HashSet<>(approved.mappings()))
                && source.queryRef()==null && source.groupKey()==null && source.rowProjection()==null
                && (source.filters()==null || source.filters().isEmpty()) && source.publishedProjectMatch()==null;
    }
    private static boolean completeSummary(SyncRunDO run,boolean allowFilteredRows) {
        if(run.getSummaryJson()==null)return false;
        var summary=JsonUtils.parseMap(run.getSummaryJson());
        for(String action:allowFilteredRows?List.of("ISSUE","CONFLICT"):List.of("ISSUE","CONFLICT","SKIPPED")) {
            var count=summary.getOrDefault(action,0);
            if(!(count instanceof Number number) || number.longValue()!=0)return false;
        }
        return true;
    }
}
