package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemDictionaryTypeMigrationApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.*;

/** P08 uses approved source interpreters with private direct SQL writers. */
@Component
@RequiredArgsConstructor
public class DppmsDirectDictionaryTask extends AbstractSynchronizeTask<DppmsDirectDictionaryTask.Execution,DataSyncAdapter.Batch,List<DataSyncAdapter.Change>> implements DataSyncAdapter {
    record Execution(Batch batch,boolean apply) {}
    private final DirectDictionaryTypeMapper types;
    private final DirectDictionaryDataMapper data;
    private final PermissionApi permissions;
    private final DataSource dataSource;
    private final SystemUserMigrationApi users;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private DppmsDictionaryTypeSyncAdapter typeAdapter() {
        return new DppmsDictionaryTypeSyncAdapter(new DirectDictionaryTypes(types,permissions,dataSource),users,tasks,runs);
    }
    private DppmsDictionaryDataSyncAdapter dataAdapter(List<SystemDictionaryTypeMigrationApi.Type> planned) {
        return new DppmsDictionaryDataSyncAdapter(new DirectDictionaryData(data,permissions,dataSource,planned),users,tasks,runs);
    }
    @Override public Descriptor descriptor() {
        var type=typeAdapter().descriptor().objects().getFirst();
        var missingFields=new ArrayList<>(type.fields());missingFields.add(new Field("sourceId","原字典项ID","LONG",true));
        var objects=new ArrayList<>(List.of(type,new ObjectDescriptor(DppmsDirectDictionaryTemplate.MISSING_TYPE,"原字典项中缺失的类型定义",
                List.copyOf(missingFields),type.targetContext(),type.targetObjectType(),type.targetTable(),false)));
        objects.addAll(dataAdapter(List.of()).descriptor().objects());
        return new Descriptor(DppmsDirectDictionaryTemplate.ADAPTER,"DPPMS基础字典类型与字典项（直写）",List.copyOf(objects),List.of("RETAIN"),List.of("UPSERT"),false);
    }
    @Override public boolean sharesTargetAcrossSources(){return true;}
    @Override public List<Change> preview(Batch batch){return syncData(new Execution(batch,false));}
    @Override public List<Change> apply(Batch batch){return syncData(new Execution(batch,true));}
    @Override public void refreshCaches(){}
    @Override protected void syncDataBefore(Execution execution) {
        var batch=execution.batch();long tenant=TenantContextHolder.getRequiredTenantId();
        String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1])
                || !batch.full() || batch.clearBeforeLoad() || batch.adoptExisting()
                || !"RETAIN".equals(batch.missingPolicy()) || !"UPSERT".equals(batch.loadingMode()))
            throw new IllegalArgumentException("字典直写仅允许完整读取、保留历史且不接管既有字典");
        var task=tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        if(!DppmsDirectDictionaryTemplate.ADAPTER.equals(task.getAdapter()) || !DppmsConsolidation.accepts(task,"DPPMS_DICTIONARY_TYPES"))
            throw new IllegalArgumentException("字典直写任务身份无效");
    }
    @Override protected Batch syncDataQuery(Execution execution){return execution.batch();}
    @Override protected List<Change> syncDataInsertWrapper(Execution execution,Batch batch) {
        var first=typeAdapter();var prepared=types(batch);
        var interpreted=execution.apply()?first.apply(prepared.batch()):first.preview(prepared.batch());
        var typeChanges=interpreted.stream().map(change->{
            Row nativeRow=prepared.nativeRows().get(change.sourceKey());
            if(nativeRow==null)throw new IllegalStateException("字典类型回执没有对应原生来源身份");
            return new Change(nativeRow.object(),nativeRow.sourceKey(),change.targetId(),change.action(),change.before(),change.after(),change.message());
        }).toList();
        var planned=typeChanges.stream().map(change->JsonUtils.parseObject(JsonUtils.toJsonString(change.after().get("type")),SystemDictionaryTypeMigrationApi.Type.class)).toList();
        var second=dataAdapter(planned);var selected=select(new Selection(batch,second));
        var itemChanges=execution.apply()?second.apply(selected):second.preview(selected);
        var result=new ArrayList<>(typeChanges);result.addAll(itemChanges);return List.copyOf(result);
    }
    private record TypePreparation(Batch batch,Map<String,Row> nativeRows) {}
    private static TypePreparation types(Batch batch) {
        String object=DppmsDictionaryTypeSyncTemplate.OBJECT;
        Set<String> objects=Set.of(object,DppmsDirectDictionaryTemplate.MISSING_TYPE);
        Map<String,Binding> logicalPrior=new LinkedHashMap<>();Map<String,Binding> nativePrior=new HashMap<>();
        for(var binding:batch.bindings())if(objects.contains(binding.object())) {
            var saved=JsonUtils.parseObject(JsonUtils.toJsonString(binding.lastFields().get("type")),SystemDictionaryTypeMigrationApi.Type.class);
            if(saved==null || saved.id()==null || !Objects.equals(saved.id(),binding.targetId()) || saved.type()==null)
                throw new IllegalArgumentException("字典类型原生绑定的完整定义缺失");
            if(nativePrior.put(binding.object()+":"+binding.sourceKey(),binding)!=null)throw new IllegalArgumentException("字典类型原生绑定重复");
            var normalized=new Binding(object,saved.type(),binding.targetId(),binding.lastFields());
            var previous=logicalPrior.putIfAbsent(saved.type(),normalized);
            if(previous!=null && (!Objects.equals(previous.targetId(),normalized.targetId())
                    || !Objects.equals(previous.lastFields().get("type"),normalized.lastFields().get("type"))))
                throw new IllegalArgumentException("同一类型编码的原生来源别名回执冲突");
        }
        Map<String,Row> nativeRows=new LinkedHashMap<>();List<Row> normalizedRows=new ArrayList<>();
        for(var row:batch.rows())if(objects.contains(row.object())) {
            SyncFieldMapper.primaryKey(row.sourceKey());
            boolean missing=DppmsDirectDictionaryTemplate.MISSING_TYPE.equals(row.object());
            if(!Objects.equals(row.sourceKey(),Objects.toString(row.fields().get(missing?"sourceId":"id"),null))
                    || missing && row.fields().get("id")!=null)throw new IllegalArgumentException("字典类型原生主键或缺失分组来源身份不同");
            String code=Objects.toString(row.fields().get("dataTypeCode"),null);
            if(code==null || code.isBlank() || nativeRows.put(code,row)!=null)throw new IllegalArgumentException("字典类型编码缺失或重复");
            var own=nativePrior.get(row.object()+":"+row.sourceKey());
            if(own!=null) {
                var saved=JsonUtils.parseObject(JsonUtils.toJsonString(own.lastFields().get("type")),SystemDictionaryTypeMigrationApi.Type.class);
                if(!code.equals(saved.type()) || !Objects.equals(own.targetId(),row.targetId()))throw new IllegalArgumentException("原生类型来源不能改码或改绑");
            } else if(row.targetId()!=null)throw new IllegalArgumentException("字典类型目标缺少原生绑定");
            var previous=logicalPrior.get(code);
            normalizedRows.add(new Row(object,code,row.fields(),previous==null?null:previous.targetId()));
        }
        return new TypePreparation(new Batch(batch.owner(),List.copyOf(normalizedRows),List.copyOf(logicalPrior.values()),true,batch.missingPolicy(),false,batch.loadingMode(),false),Map.copyOf(nativeRows));
    }
    @Override protected void syncDataSuccess(Execution execution,List<Change> result) {
        if(execution.apply() && result.stream().anyMatch(change->Set.of("ISSUE","CONFLICT").contains(change.action())))
            throw new IllegalArgumentException("字典直写存在问题，整批回滚");
    }
    private record Selection(Batch batch,DataSyncAdapter adapter) {}
    private static Batch select(Selection selection) {
        var batch=selection.batch();Set<String> names=new HashSet<>();selection.adapter().descriptor().objects().forEach(object->names.add(object.name()));
        return new Batch(batch.owner(),batch.rows().stream().filter(row->names.contains(row.object())).toList(),
                batch.bindings().stream().filter(binding->names.contains(binding.object())).toList(),true,batch.missingPolicy(),false,batch.loadingMode(),false);
    }
}
