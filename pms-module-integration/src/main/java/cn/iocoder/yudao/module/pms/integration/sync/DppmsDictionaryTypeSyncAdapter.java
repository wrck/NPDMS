package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDictionaryTypeSyncTemplate.*;

@Component @RequiredArgsConstructor
public class DppmsDictionaryTypeSyncAdapter implements DataSyncAdapter {
    private final SystemDictionaryTypeMigrationApi types;
    private final SystemUserMigrationApi users;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    @Override public Descriptor descriptor(){
        var columns=new ArrayList<>(ORIGINAL_COLUMNS);columns.add(MISSING);
        return new Descriptor(ADAPTER,"DPPMS基础字典类型",List.of(new ObjectDescriptor(OBJECT,"原类型及已批准缺失分组定义",
                columns.stream().map(name->new Field(name,name,"STRING",Set.of("dataTypeCode","dataTypeName",MISSING).contains(name))).toList(),
                "SYSTEM","DictType","system_dict_type",false)),List.of("RETAIN"),List.of("UPSERT"),false);
    }
    @Override public void refreshCaches(){}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply){
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch.clearBeforeLoad() || batch.adoptExisting() || !"RETAIN".equals(batch.missingPolicy()) || !"UPSERT".equals(batch.loadingMode()))
            fail("禁止清空、缺项删除或接管现有全局类型");
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
                || run.getCreateTime()==null) fail("运行、配置版本或实际迁入审计时间缺失");
        Long actor=SyncFieldMapper.primaryKey(run.getCreator());
        var previous=new HashMap<String,Binding>();
        for(var binding:batch.bindings()) if(!OBJECT.equals(binding.object()) || previous.put(binding.sourceKey(),binding)!=null) fail("来源绑定重复或对象不符");
        var actors=new HashSet<String>();
        for(var row:batch.rows()) for(String name:List.of("createBy","updateBy")){
            String code=text(row.fields().get(name));if(code!=null) actors.add(code);
        }
        var actorIds=new HashMap<String,Long>();
        if(!actors.isEmpty()) for(var identity:users.getHistoricalIdentitiesByUsername(tenant,actors)){
            if(!actors.contains(identity.username()) || actorIds.put(identity.username(),identity.id())!=null) fail("审计人员精确身份不唯一");
        }
        if(!actorIds.keySet().equals(actors)) fail("原审计人员没有已核实的平台账号，禁止猜配");
        var entries=new ArrayList<Entry>();var originals=new LinkedHashMap<String,Map<String,Object>>();
        for(var row:batch.rows()){
            var fields=row.fields();String code=text(fields.get("dataTypeCode"));String missing=text(fields.get(MISSING));
            if(!OBJECT.equals(row.object()) || !fields.keySet().containsAll(ORIGINAL_COLUMNS) || !fields.containsKey(MISSING)
                    || !Objects.equals(code,row.sourceKey()) || !Set.of("0","1").contains(missing)
                    || fields.containsKey("_sourcePrimaryKey")) fail("原类型完整字段或分组编码身份无效");
            var raw=original(fields,"1".equals(missing));
            if(originals.put(row.sourceKey(),raw)!=null) fail("分组编码重复");
            var prior=previous.get(row.sourceKey());
            if(row.targetId()!=null && prior==null || prior!=null && !Objects.equals(row.targetId(),prior.targetId())) fail("目标ID与原绑定不一致");
            Type last=prior==null?null:decode(prior.lastFields().get("type"));
            if(prior!=null && (last==null || !sameOriginal(raw,prior.lastFields().get("originalFields")))) fail("原定义或来源分组已变，等待裁决");
            LocalDateTime created,updated;String creator,updater;
            if("1".equals(missing)){
                if(text(fields.get("id"))!=null || !Objects.equals(code,text(fields.get("dataTypeName")))
                        || List.of("status","createTime","createBy","updateTime","updateBy","effectiveFrom","effectiveTo")
                        .stream().anyMatch(name->text(fields.get(name))!=null)) fail("缺失定义不能伪造原审计或名称");
                created=last==null?run.getCreateTime().withNano(0):last.createTime();updated=last==null?created:last.updateTime();
                creator=last==null?actor.toString():last.creator();updater=last==null?actor.toString():last.updater();
            }else{
                SyncFieldMapper.primaryKey(text(fields.get("id")));
                created=SyncFieldMapper.sourceTime(fields.get("createTime"));
                updated=fields.get("updateTime")==null?created:SyncFieldMapper.sourceTime(fields.get("updateTime"));
                creator=actor(fields.get("createBy"),actorIds);updater=actor(fields.get("updateBy"),actorIds);
            }
            entries.add(new Entry(row.sourceKey(),row.targetId(),last,new Type(null,text(fields.get("dataTypeName")),code,0,null,
                    creator,created,updater,updated,false,null)));
        }
        var request=new Request(tenant,actor,batch.owner(),entries);
        return (apply?types.apply(request):types.preview(request)).stream().map(result->new Change(OBJECT,result.sourceKey(),
                result.after().id(),result.action(),encode(result.before()),Map.of("type",encode(result.after()),
                "originalFields",originals.get(result.sourceKey())),"正式系统字典类型；已裁决管理标记及有效期不迁入")).toList();
    }
    private static String actor(Object raw,Map<String,Long> ids){String code=text(raw);return code==null?null:ids.get(code).toString();}
    private static Map<String,Object> original(Map<String,Object> fields,boolean missing){
        var result=new LinkedHashMap<String,Object>();result.put(MISSING,missing?"1":"0");
        if(missing){result.put("dataTypeCode",text(fields.get("dataTypeCode")));return result;}
        for(String name:ORIGINAL_COLUMNS){Object value=fields.get(name);
            if(value!=null && Set.of("createTime","updateTime","effectiveFrom","effectiveTo").contains(name)) value=SyncFieldMapper.sourceTime(value).toString();
            result.put(name,value==null?tools.jackson.databind.node.NullNode.getInstance():value.toString());}
        return result;
    }
    private static boolean sameOriginal(Map<String,Object> expected,Object stored){
        if(!(stored instanceof Map<?,?> prior) || !prior.keySet().equals(expected.keySet())) return false;
        return expected.keySet().stream().allMatch(name->Objects.equals(text(expected.get(name)),text(prior.get(name))));
    }
    private static Type decode(Object raw){return raw instanceof Map<?,?>?JsonUtils.parseObject(JsonUtils.toJsonString(raw),Type.class):null;}
    private static Map<String,Object> encode(Type type){return type==null?Map.of():JsonUtils.parseMap(JsonUtils.toJsonString(type));}
    private static String text(Object value){return value==null || value instanceof tools.jackson.databind.JsonNode node && node.isNull()?null:value.toString();}
    private static void fail(String message){throw new IllegalArgumentException("DPPMS字典类型："+message);}
}
