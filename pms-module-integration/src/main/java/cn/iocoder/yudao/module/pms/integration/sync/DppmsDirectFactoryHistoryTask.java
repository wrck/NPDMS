package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsFactoryHistoryMigrationApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsFactoryHistoryMigrationApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDirectFactoryHistoryTemplate.*;

@Component
public class DppmsDirectFactoryHistoryTask implements DataSyncAdapter {
    private final SyncOwnerRunContext context;
    private final DirectFactoryHistoryPersistence owner;
    public DppmsDirectFactoryHistoryTask(SyncOwnerRunContext context,javax.sql.DataSource source,
            cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectFactoryHistoryMapper mapper,
            cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions,cn.iocoder.yudao.module.system.api.user.AdminUserApi users) {
        this.context=context;this.owner=new DirectFactoryHistoryPersistence(source,mapper,permissions,users);
    }
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public Descriptor descriptor() {
        return new Descriptor(ADAPTER,"DPPMS全量出厂版本历史",List.of(new ObjectDescriptor(OBJECT,"出厂版本原行",
                COLUMNS.stream().map(n->new Field(n,n,"STRING",false)).toList(),"AST","DeviceFactoryVersionRecord",
                "ast_device_factory_version_record",false)),List.of("RETAIN"),List.of("UPSERT"),false,false);
    }
    @Override public void refreshCaches() {}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        if(batch==null || !batch.full())throw new IllegalArgumentException("DIRECT_FULL_SOURCE_REQUIRED");
        var run=context.require(new SyncOwnerRunContext.Request(batch,ADAPTER,apply));
        var previous=new HashMap<String,Binding>();
        for(var binding:batch.bindings())if(!OBJECT.equals(binding.object()) || previous.put(binding.sourceKey(),binding)!=null)fail();
        var rows=new LinkedHashMap<String,Row>();var originals=new HashMap<String,Map<String,Object>>();
        var entries=new ArrayList<Entry>();
        for(var row:batch.rows()) {
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(COLUMNS) || rows.put(row.sourceKey(),row)!=null)fail();
            var original=new LinkedHashMap<String,Object>();
            COLUMNS.forEach(name->original.put(name,text(row.fields().get(name))==null?tools.jackson.databind.node.NullNode.getInstance():text(row.fields().get(name))));
            var binding=previous.get(row.sourceKey());
            if(binding==null && row.targetId()!=null || binding!=null && (!Objects.equals(binding.targetId(),row.targetId())
                    || !(binding.lastFields().get("originalFields") instanceof Map<?,?> before)
                    || !before.keySet().equals(Set.copyOf(COLUMNS)) || COLUMNS.stream().anyMatch(n->!Objects.equals(text(before.get(n)),text(original.get(n))))))fail();
            originals.put(row.sourceKey(),original);
            entries.add(new Entry(row.sourceKey(),new Values(text(original.get("serial_number")),text(original.get("conp")),
                    text(original.get("cpld")),text(original.get("boot")),text(original.get("pcb"))),row.targetId()));
        }
        var result=new LinkedHashMap<String,Change>();
        for(int offset=0;offset<entries.size();offset+=500) {
            var command=new Command(run.tenantId(),run.actorId(),entries.subList(offset,Math.min(offset+500,entries.size())));
            var receipts=apply?owner.apply(command):owner.preview(command);
            if(receipts.size()!=command.entries().size())fail();
            for(var receipt:receipts) {
                if(!rows.containsKey(receipt.sourceKey()) || result.containsKey(receipt.sourceKey()))fail();
                var binding=previous.get(receipt.sourceKey());
                result.put(receipt.sourceKey(),new Change(OBJECT,receipt.sourceKey(),receipt.targetId(),receipt.action(),
                        binding==null?Map.of():binding.lastFields(),Map.of("originalFields",originals.get(receipt.sourceKey())),"保留完整出厂版本原行及重复次数"));
            }
        }
        return batch.rows().stream().map(row->result.get(row.sourceKey())).toList();
    }
    private static String text(Object value){return value==null || value instanceof tools.jackson.databind.JsonNode n && n.isNull()?null:value.toString();}
    private static void fail(){throw new IllegalArgumentException("DPPMS_FACTORY_HISTORY_BINDING_INVALID");}
}
