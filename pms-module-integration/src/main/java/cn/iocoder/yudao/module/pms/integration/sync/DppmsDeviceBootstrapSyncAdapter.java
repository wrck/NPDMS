package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceBootstrapApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDeviceBootstrapSyncTemplate.*;

@Component @RequiredArgsConstructor
public class DppmsDeviceBootstrapSyncAdapter implements DataSyncAdapter {
    private final SyncOwnerRunContext context;
    private final DppmsDeviceBootstrapApi owner;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public Descriptor descriptor() {
        return new Descriptor(ADAPTER,"DPPMS设备基础承接",List.of(new ObjectDescriptor(BASE,"本地发货设备基础",
                COLUMNS.stream().map(n->new Field(n,n,"STRING",false)).toList(),"AST","Device","ast_device",false)),
                List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override public void refreshCaches() {}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        var run=context.require(new SyncOwnerRunContext.Request(batch,ADAPTER,apply));
        var rows=new LinkedHashMap<String,Row>();var bindings=new HashMap<String,Binding>();
        for(var binding:batch.bindings())if(!BASE.equals(binding.object()) || bindings.put(binding.sourceKey(),binding)!=null)fail();
        var seeds=new ArrayList<DppmsDeviceBootstrapApi.Seed>();
        for(var row:batch.rows()) {
            if(!BASE.equals(row.object()) || !row.fields().keySet().containsAll(COLUMNS) || rows.put(row.sourceKey(),row)!=null)fail();
            var seed=JsonUtils.parseObject(JsonUtils.toJsonString(row.fields()),DppmsDeviceBootstrapApi.Seed.class);
            if(!row.sourceKey().equals(seed.deviceSn()))fail();
            var previous=bindings.get(row.sourceKey());
            if(previous==null && row.targetId()!=null || previous!=null && !Objects.equals(row.targetId(),previous.targetId()))fail();
            seeds.add(seed);
        }
        var results=new LinkedHashMap<String,Change>();
        for(int offset=0;offset<seeds.size();offset+=500) {
            var command=new DppmsDeviceBootstrapApi.BaseCommand(run.tenantId(),run.actorId(),"sync:"+run.run().getId(),
                    run.run().getSourceUpper()==null?run.run().getStartedAt():run.run().getSourceUpper(),
                    seeds.subList(offset,Math.min(offset+500,seeds.size())));
            var receipts=apply?owner.applyBase(command):owner.previewBase(command);
            if(receipts.size()!=command.seeds().size())fail();
            for(var receipt:receipts) {
                var row=rows.get(receipt.deviceSn());var prior=bindings.get(receipt.deviceSn());
                if(row==null || results.containsKey(receipt.deviceSn()) || prior!=null && !Objects.equals(prior.targetId(),receipt.deviceId()))fail();
                var after=new LinkedHashMap<String,Object>();
                COLUMNS.forEach(name->after.put(name,row.fields().get(name)==null?tools.jackson.databind.node.NullNode.getInstance():row.fields().get(name)));
                after.put("deviceId",receipt.deviceId());after.put("assignmentVersion",receipt.assignmentVersion());
                results.put(receipt.deviceSn(),new Change(BASE,receipt.deviceSn(),receipt.deviceId(),receipt.action(),
                        prior==null?Map.of():prior.lastFields(),after,"从本地发货事实承接设备基础"));
            }
        }
        return batch.rows().stream().map(row->results.get(row.sourceKey())).toList();
    }
    private static void fail(){throw new IllegalArgumentException("DPPMS_DEVICE_BOOTSTRAP_BINDING_INVALID");}
}
