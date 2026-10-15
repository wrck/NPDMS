package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryDataMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryDataMigrationApi.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDictionaryDataSyncTemplate.*;

@Component @RequiredArgsConstructor
public class DppmsDictionaryDataSyncAdapter implements DataSyncAdapter {
    private final SystemDictionaryDataMigrationApi data;
    private final SystemUserMigrationApi users;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    @Override public Descriptor descriptor(){
        return new Descriptor(ADAPTER,"DPPMS基础字典项",List.of(new ObjectDescriptor(OBJECT,"原字典项完整12列",
                ORIGINAL_COLUMNS.stream().map(name->new Field(name,name,"STRING",false)).toList(),
                "SYSTEM","DictData","system_dict_data",false)),List.of("RETAIN"),List.of("UPSERT"),false);
    }
    @Override public void refreshCaches(){}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply){
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch==null || batch.clearBeforeLoad() || batch.adoptExisting() || !"RETAIN".equals(batch.missingPolicy()) || !"UPSERT".equals(batch.loadingMode()))
            fail("禁止清空、缺项删除或接管现有全局字典项");
        String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1])) fail("来源所有权无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        var run=task.getActiveRunId()==null?null:runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(!Objects.equals(task.getTenantId(),tenant) || !DppmsConsolidation.accepts(task,ADAPTER) || !"DPPMS".equals(task.getSourceSystem())
                || definition==null || !"ONCE".equals(definition.mode()) || definition.resetMappingsBeforeLoad()
                || run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus())
                || run.getCreateTime()==null) fail("运行、配置版本或实际迁入时间无效");
        Long actor=SyncFieldMapper.primaryKey(run.getCreator());
        var priorByKey=new HashMap<String,Binding>();
        for(var binding:batch.bindings()) if(!OBJECT.equals(binding.object()) || priorByKey.put(binding.sourceKey(),binding)!=null) fail("来源绑定对象错误或重复");
        var actorNames=new HashSet<String>();var sourceKeys=new HashSet<String>();
        for(var row:batch.rows()){
            var fields=row.fields();
            if(!OBJECT.equals(row.object()) || !fields.keySet().containsAll(ORIGINAL_COLUMNS)
                    || fields.containsKey("_sourcePrimaryKey") || !Objects.equals(row.sourceKey(),text(fields.get("id")))
                    || !sourceKeys.add(row.sourceKey())) fail("原12字段或原ID身份缺失、重复");
            SyncFieldMapper.primaryKey(row.sourceKey());
            for(String name:List.of("createBy","updateBy")){
                String code=text(fields.get(name));
                if(code!=null && !("542".equals(row.sourceKey()) && "createBy".equals(name) && "system".equals(code))) actorNames.add(code);
            }
        }
        var actorIds=new HashMap<String,Long>();
        if(!actorNames.isEmpty()) for(var identity:users.getHistoricalIdentitiesByUsername(tenant,actorNames))
            if(!actorNames.contains(identity.username()) || actorIds.put(identity.username(),identity.id())!=null) fail("原审计人员身份不唯一");
        if(!actorIds.keySet().equals(actorNames)) fail("原人员缺少已核实的平台账号，等待裁决");
        var entries=new ArrayList<Entry>();var originals=new LinkedHashMap<String,Map<String,Object>>();var skipped=new ArrayList<Change>();
        for(var row:batch.rows()){
            var fields=row.fields();var raw=original(fields);originals.put(row.sourceKey(),raw);
            var prior=priorByKey.get(row.sourceKey());
            if("114".equals(row.sourceKey())){
                if(!"23".equals(text(fields.get("dataTypeCode"))) || ORIGINAL_COLUMNS.stream().filter(n->!Set.of("id","dataTypeCode").contains(n))
                        .anyMatch(n->text(fields.get(n))!=null) || row.targetId()!=null || prior!=null) fail("已裁决空记录114已变，不能继续排除");
                skipped.add(new Change(OBJECT,row.sourceKey(),null,"SKIPPED",Map.of(),Map.of("originalFields",raw),
                        "用户明确不迁入：无业务键、标签或属性，不构成字典项"));continue;
            }
            if(row.targetId()!=null && prior==null || prior!=null && !Objects.equals(row.targetId(),prior.targetId())) fail("目标与正式来源绑定不一致");
            Item last=prior==null?null:decode(prior.lastFields().get("item"));
            if(prior!=null && (last==null || !sameOriginal(raw,prior.lastFields().get("originalFields")))) fail("原字段发生后续变化，不自动接管");
            LocalDateTime created=fields.get("createTime")==null?last==null?run.getCreateTime().withNano(0):last.createTime():time(fields.get("createTime"));
            LocalDateTime updated=fields.get("updateTime")==null?created:time(fields.get("updateTime"));
            String creator="542".equals(row.sourceKey()) && "system".equals(text(fields.get("createBy")))?"system":actor(fields.get("createBy"),actorIds);
            int status=last==null?initialStatus(fields,run.getCreateTime()):last.status();
            Integer sort=fields.get("sortId")==null?0:integer(fields.get("sortId"));
            entries.add(new Entry(row.sourceKey(),row.targetId(),last,new Item(null,sort,text(fields.get("basicDataName")),
                    text(fields.get("basicDataId")),text(fields.get("dataTypeCode")),status,null,null,null,creator,created,
                    actor(fields.get("updateBy"),actorIds),updated,false)));
        }
        var results=apply?data.apply(new Request(tenant,actor,batch.owner(),entries)):data.preview(new Request(tenant,actor,batch.owner(),entries));
        if(results.size()!=entries.size() || !new HashSet<>(results.stream().map(Result::sourceKey).toList())
                .equals(new HashSet<>(entries.stream().map(Entry::sourceKey).toList()))) fail("SYSTEM回执身份或数量不一致");
        var changes=new ArrayList<Change>();
        for(var result:results) changes.add(new Change(OBJECT,result.sourceKey(),result.after().id(),result.action(),encode(result.before()),
                Map.of("item",encode(result.after()),"originalFields",originals.get(result.sourceKey())),
                "正式系统字典项；用户裁决basicDataAttri1/effectiveFrom/effectiveTo不迁入，不执行旧入口"));
        changes.addAll(skipped);return List.copyOf(changes);
    }
    private static int initialStatus(Map<String,Object> fields,LocalDateTime now){
        LocalDateTime start=time(fields.get("effectiveFrom")),end=time(fields.get("effectiveTo"));
        if(start!=null && end!=null && end.isBefore(start)) fail("原有效期逆序，等待裁决");
        return start!=null && now.isBefore(start) || end!=null && !now.isBefore(end)?1:0;
    }
    private static Integer integer(Object value){
        String text=text(value);if(text==null || !text.matches("-?[0-9]+")) throw new IllegalArgumentException("原排序不是整数");return Integer.valueOf(text);
    }
    private static String actor(Object raw,Map<String,Long> ids){String code=text(raw);return code==null?null:ids.get(code).toString();}
    private static LocalDateTime time(Object raw){return raw==null?null:SyncFieldMapper.sourceTime(raw);}
    private static Map<String,Object> original(Map<String,Object> fields){
        var result=new LinkedHashMap<String,Object>();
        for(String name:ORIGINAL_COLUMNS){Object value=fields.get(name);
            if(value!=null && Set.of("createTime","updateTime","effectiveFrom","effectiveTo").contains(name)) value=time(value).toString();
            result.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value.toString());}
        return result;
    }
    private static boolean sameOriginal(Map<String,Object> expected,Object stored){
        if(!(stored instanceof Map<?,?> before) || !before.keySet().equals(expected.keySet())) return false;
        return expected.keySet().stream().allMatch(name->Objects.equals(text(expected.get(name)),text(before.get(name))));
    }
    private static Item decode(Object raw){return raw instanceof Map<?,?>?JsonUtils.parseObject(JsonUtils.toJsonString(raw),Item.class):null;}
    private static Map<String,Object> encode(Item row){
        if(row==null) return Map.of();
        var result=new LinkedHashMap<String,Object>(JsonUtils.parseMap(JsonUtils.toJsonString(row)));
        for(String name:List.of("id","sort","label","value","dictType","status","colorType","cssClass","remark",
                "creator","createTime","updater","updateTime","deleted")) result.putIfAbsent(name,tools.jackson.databind.node.NullNode.getInstance());
        return result;
    }
    private static String text(Object raw){return raw==null || raw instanceof tools.jackson.databind.JsonNode node && node.isNull()?null:raw.toString();}
    private static void fail(String message){throw new IllegalArgumentException("DPPMS字典项："+message);}
}
