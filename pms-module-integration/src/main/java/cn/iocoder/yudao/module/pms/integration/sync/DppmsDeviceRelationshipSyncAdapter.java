package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceSourceRelationshipApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceSourceRelationshipApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDeviceRelationshipSyncTemplate.*;

/** INT owns run/binding evidence. All relationship business reads and writes belong to AST. */
@Component @RequiredArgsConstructor
public class DppmsDeviceRelationshipSyncAdapter implements DataSyncAdapter {
    private final DppmsDeviceSourceRelationshipApi relationships;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public void refreshCaches(){}
    @Override public Descriptor descriptor(){
        return new Descriptor(ADAPTER,"DPPMS主附SN正式关系",List.of(new ObjectDescriptor(OBJECT,"原8列主附SN关系",
                ORIGINAL_COLUMNS.stream().map(name->new Field(name,name,"STRING",
                        Set.of("id","sn1","sn2","createtime").contains(name))).toList(),
                "AST","DeviceRelationship","ast_device_relationship",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply){
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy())
                || batch.rows().size()>2000) fail("加载策略或批量无效");
        String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1])) fail("来源所有权无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!Objects.equals(task.getTenantId(),tenant) || !ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem())
                || definition==null || !"ONCE".equals(definition.mode()) || definition.resetMappingsBeforeLoad()
                || run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))
            fail("任务、运行身份、阶段或配置版本失效");
        Long actor=SyncFieldMapper.primaryKey(run.getCreator());
        var previous=new HashMap<String,Binding>();
        for(var binding:batch.bindings()) if(!OBJECT.equals(binding.object())
                || previous.put(binding.sourceKey(),binding)!=null) fail("来源绑定重复或对象不符");
        var raw=new LinkedHashMap<String,Map<String,Object>>();
        var sources=new ArrayList<Relation>();
        for(var row:batch.rows()){
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(ORIGINAL_COLUMNS)
                    || !Objects.equals(row.sourceKey(),text(row.fields().get("id"))) || row.fields().containsKey("_sourcePrimaryKey"))
                fail("原8列或原键缺失；原自增ID不得用作业务主键");
            var original=original(row.fields());
            if(raw.put(row.sourceKey(),original)!=null) fail("原键重复");
            var prior=previous.get(row.sourceKey());
            if(row.targetId()!=null && prior==null || prior!=null && !Objects.equals(row.targetId(),prior.targetId()))
                fail("目标ID与同步绑定不一致");
            if(prior!=null && (!prior.lastFields().containsKey("relationship")
                    || !sameOriginal(original,prior.lastFields().get("originalFields")))) fail("原关系或绑定证据已变");
            sources.add(new Relation(SyncFieldMapper.primaryKey(row.sourceKey()),text(row.fields().get("sn1")),text(row.fields().get("item1")),
                    text(row.fields().get("sn2")),text(row.fields().get("item2")),text(row.fields().get("contract")),
                    text(row.fields().get("createtime")),text(row.fields().get("updatetime"))));
        }
        if(sources.isEmpty()) return List.of();
        var command=new Command(tenant,actor,sources);
        var checked=relationships.previewRelationships(command);
        if(checked.size()!=sources.size()) fail("Owner回执数量不符");
        var checkedKeys=new HashSet<String>();
        for(var result:checked){
            if(!raw.containsKey(result.sourceKey()) || !checkedKeys.add(result.sourceKey()) || result.after()==null) fail("Owner回执身份不符");
            var prior=previous.get(result.sourceKey());
            if(prior==null && result.before()!=null && !batch.adoptExisting()) fail("既有关系接管未明确开启");
            if(prior!=null && (result.before()==null || !Objects.equals(result.before().id(),prior.targetId())
                    || !encode(result.before()).equals(prior.lastFields().get("relationship")))) fail("目标关系发生后续变化");
        }
        var results=apply?relationships.applyRelationships(command):checked;
        if(results.size()!=checked.size()) fail("写入回执数量不符");
        var changes=new ArrayList<Change>();var resultKeys=new HashSet<String>();
        for(var result:results){
            if(!checkedKeys.contains(result.sourceKey()) || !resultKeys.add(result.sourceKey()) || result.after()==null) fail("写入回执身份不符");
            changes.add(new Change(OBJECT,result.sourceKey(),result.after().id(),result.action(),encode(result.before()),
                    Map.of("relationship",encode(result.after()),"originalFields",raw.get(result.sourceKey())),
                    "正式AST关系承接原8列；不调整设备归属、当前装配或既有审计"));
        }
        return List.copyOf(changes);
    }
    private static Map<String,Object> original(Map<String,Object> fields){
        var result=new LinkedHashMap<String,Object>();
        for(String name:ORIGINAL_COLUMNS){var value=text(fields.get(name));result.put(name,value==null
                ?tools.jackson.databind.node.NullNode.getInstance():value);}
        return result;
    }
    private static Map<String,Object> encode(RelationshipRecord record){
        return record==null?Map.of():JsonUtils.parseMap(JsonUtils.toJsonString(record));
    }
    private static boolean sameOriginal(Map<String,Object> expected,Object stored){
        if(!(stored instanceof Map<?,?> prior) || !prior.keySet().containsAll(ORIGINAL_COLUMNS)) return false;
        return ORIGINAL_COLUMNS.stream().allMatch(name->Objects.equals(text(expected.get(name)),text(prior.get(name))));
    }
    private static String text(Object value){return value==null || value instanceof tools.jackson.databind.JsonNode node && node.isNull()?null:value.toString();}
    private static void fail(String message){throw new IllegalArgumentException("DPPMS主附SN："+message);}
}
