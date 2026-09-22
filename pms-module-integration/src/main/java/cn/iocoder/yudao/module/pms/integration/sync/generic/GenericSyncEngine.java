package cn.iocoder.yudao.module.pms.integration.sync.generic;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.GenericSyncJdbcStore;
import cn.iocoder.yudao.module.pms.integration.sync.SyncDefinition;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Component
@RequiredArgsConstructor
public class GenericSyncEngine {
    public static final String KEY = "TABLE_MAPPING";
    private static final Set<String> MODES = Set.of("UPSERT","INSERT_ONLY","INSERT_IGNORE","UPDATE_ONLY");
    private static final Set<String> CONVERSIONS = Set.of("DIRECT","STRING","TRIM","LONG","DECIMAL","BOOLEAN","DATETIME","DATE","ENUM","LOOKUP","CONSTANT","JSON");
    private final GenericTargetCatalog catalog;
    private final GenericSyncJdbcStore store;
    private final org.springframework.context.ApplicationEventPublisher events;

    public DataSyncAdapter.Descriptor descriptor() {
        return new DataSyncAdapter.Descriptor(KEY,"通用表映射与迁移",List.of(),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    public DataSyncAdapter bind(SyncDefinition definition) { validate(definition);return new Execution(definition); }
    public void validate(SyncDefinition d) {
        if (d.taskKey() == null || !d.taskKey().matches("[A-Za-z0-9_-]{1,40}")) throw new IllegalArgumentException("通用任务标识须为 1 至 40 位字母、数字、下划线或横线");
        if (d.clearBeforeLoad() || d.resetMappingsBeforeLoad() || !"UPSERT".equals(d.loadingMode()) || !"RETAIN".equals(d.missingPolicy()))
            throw new IllegalArgumentException("通用引擎保留已有数据；写入方式由各步骤配置，不支持清空和重置映射");
        if (d.sources() == null || d.sources().isEmpty() || d.sources().size()>10) throw new IllegalArgumentException("须配置 1 至 10 个来源");
        for (var source : d.sources()) {
            GenericTargetCatalog.identifier(source.object());
            if(source.issueField()!=null)GenericTargetCatalog.identifier(source.issueField());
            if (source.syncPrimaryKey()) throw new IllegalArgumentException("通用引擎使用目标生成主键及业务唯一键，不替换目标主键");
            if (source.targets()==null || source.targets().isEmpty() || source.targets().size()>10) throw new IllegalArgumentException("须配置 1 至 10 个写入步骤");
            Set<String> names = new HashSet<>();
            for (var step : source.targets()) {
                if (step.name()==null || !step.name().matches("[A-Za-z][A-Za-z0-9_]{0,31}") || names.contains(step.name())) throw new IllegalArgumentException("步骤名称无效或重复");
                var table=catalog.required(step.table());
                if (!table.writable()) throw new IllegalArgumentException("目标只允许关联读取");
                if (step.keys()==null || table.keys().stream().noneMatch(k->new HashSet<>(k).equals(new HashSet<>(step.keys()))) || new HashSet<>(step.keys()).size()!=step.keys().size())
                    throw new IllegalArgumentException("目标须使用目录中的业务唯一键");
                if (!MODES.contains(step.mode()) || !Set.of("IGNORE","OVERWRITE").contains(step.nullPolicy())) throw new IllegalArgumentException("写入方式或空值策略无效");
                if (step.updateColumns()==null || !table.updateColumns().containsAll(step.updateColumns()) || !Collections.disjoint(step.keys(),step.updateColumns())) throw new IllegalArgumentException("包含禁止更新的字段或业务键");
                if (step.mappings()==null || step.mappings().isEmpty()) throw new IllegalArgumentException("缺少目标字段映射");
                Set<String> mapped=new HashSet<>();
                for(var mapping:step.mappings()) {
                    if (!table.columns().contains(mapping.target()) || !mapped.add(mapping.target()) || table.insertDefaults().containsKey(mapping.target())) throw new IllegalArgumentException("目标字段未开放、重复或由系统初始化: "+mapping.target());
                    if(!CONVERSIONS.contains(mapping.conversion())) throw new IllegalArgumentException("字段转换无效");
                    if(!"CONSTANT".equals(mapping.conversion())) reference(mapping.source(),names);
                    if(Set.of("ENUM","LOOKUP").contains(mapping.conversion()) && mapping.values()==null) throw new IllegalArgumentException("缺少字典映射");
                }
                if(step.lookups()!=null) for(var lookup:step.lookups()) {
                    var lookupTable=catalog.required(lookup.table());
                    if(lookup.match()==null || lookup.match().isEmpty() || lookup.outputs()==null || lookup.outputs().isEmpty() || !Set.of("RETAIN","ERROR","SKIP_STEP").contains(lookup.onMissing())) throw new IllegalArgumentException("关联查找配置无效");
                    if(!lookupTable.columns().containsAll(lookup.match().keySet()) || lookup.match().values().stream().anyMatch(v->!v.startsWith("@") && !mapped.contains(v))) throw new IllegalArgumentException("关联条件字段无效");
                    lookup.match().values().stream().filter(v->v.startsWith("@")).forEach(v->reference(v.substring(1),names));
                    for(var output:lookup.outputs().entrySet()) {
                        if(!table.columns().contains(output.getKey()) || table.insertDefaults().containsKey(output.getKey()) || !(lookupTable.columns().contains(output.getValue())||"id".equals(output.getValue()))) throw new IllegalArgumentException("关联输出字段无效");
                        mapped.add(output.getKey());
                    }
                }
                if(!mapped.containsAll(step.keys()) || !mapped.containsAll(step.updateColumns())) throw new IllegalArgumentException("唯一键或更新列未映射");
                if(step.whenField()!=null) reference(step.whenField(),names);
                if(step.newerBy()!=null && (!mapped.contains(step.newerBy()) || !step.updateColumns().contains(step.newerBy()))) throw new IllegalArgumentException("最新值排序列须参与映射及更新");
                if(step.tieBreaker()!=null && (step.newerBy()==null || !mapped.contains(step.tieBreaker()) || !step.updateColumns().contains(step.tieBreaker())))throw new IllegalArgumentException("同时间比较列须参与映射及更新");
                names.add(step.name());
            }
            if(source.primaryTarget()!=null && !names.contains(source.primaryTarget()))throw new IllegalArgumentException("主目标步骤不存在");
        }
    }
    private static void reference(String ref,Set<String> steps) {
        if (Set.of("$SOURCE_KEY","$SOURCE_SYSTEM","$ROW").contains(Objects.toString(ref,"")))return;
        if (ref!=null && ref.startsWith("$")) {
            var parts=ref.substring(1).split("\\.");
            if(parts.length!=2 || !steps.contains(parts[0]) || !"id".equals(parts[1])) throw new IllegalArgumentException("只可引用前序步骤的目标 ID");
        } else GenericTargetCatalog.identifier(ref);
    }

    private class Execution implements DataSyncAdapter {
        private final SyncDefinition definition;
        Execution(SyncDefinition definition) {this.definition=definition;}
        @Override public Descriptor descriptor() {
            return new Descriptor(KEY,"通用表映射与迁移",definition.sources().stream().map(source->{
                var primary=source.primaryTarget()==null?source.targets().getLast():source.targets().stream().filter(t->t.name().equals(source.primaryTarget())).findFirst().orElseThrow();
                var table=catalog.required(primary.table());
                return new ObjectDescriptor(source.object(),source.object(),List.of(),table.context(),table.table(),table.table(),false);
            }).toList(),List.of("RETAIN"),List.of("UPSERT"),false,true);
        }
        @Override public boolean requiresAllBindings(){return false;}
        @Override public boolean sharesTargetAcrossSources(){return true;}
        @Override public boolean supportsStreaming(){return true;}
        @Override public void refreshCaches(){}
        @Override public List<Change> preview(Batch batch){return execute(batch,false);}
        @Override public List<Change> apply(Batch batch){
            if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("目标写入必须位于同步分块事务中");
            return execute(batch,true);
        }
        private List<Change> execute(Batch batch,boolean apply) {
            if(batch.adoptExisting()||batch.clearBeforeLoad())throw new IllegalArgumentException("通用任务不能接管或清空目标");
            long tenant=TenantContextHolder.getRequiredTenantId();
            long previewId=-1;
            Map<String,Map<String,GenericSyncJdbcStore.Column>> schemas=new HashMap<>();
            for(var source:definition.sources())for(var step:source.targets())schemas.computeIfAbsent(step.table(),name->store.columns(catalog.required(name)));
            Map<String,Map<String,Object>> overlay=new HashMap<>();
            List<Mutation> mutations=new ArrayList<>();List<Change> changes=new ArrayList<>();
            for(var row:batch.rows()) {
                var source=definition.sources().stream().filter(s->s.object().equals(row.object())).findFirst().orElseThrow();
                String primaryStep=source.primaryTarget()==null?source.targets().getLast().name():source.primaryTarget();
                Map<String,Map<String,Object>> local=new HashMap<>(overlay);
                List<Mutation> rowWrites=new ArrayList<>();List<Map<String,Object>> refs=new ArrayList<>();
                Map<String,Object> variables=new HashMap<>(row.fields());
                variables.put("$SOURCE_KEY",row.sourceKey());variables.put("$SOURCE_SYSTEM",definition.sourceSystem());variables.put("$ROW",row.fields().getOrDefault("_syncSourcePayload",row.fields()));
                Map<String,Object> beforeValues=new LinkedHashMap<>(),afterValues=new LinkedHashMap<>();
                List<String> warnings=new ArrayList<>();Long primary=null;String action="UNCHANGED";
                try {
                    if(source.issueField()!=null && !row.fields().containsKey(source.issueField()))throw new IllegalArgumentException("来源缺少预检字段: "+source.issueField());
                    if(source.issueField()!=null && present(row.fields().get(source.issueField()))) throw new IllegalArgumentException(row.fields().get(source.issueField()).toString());
                    for(var step:source.targets()) {
                        if(step.whenField()!=null && !present(variables.get(step.whenField()))) {if(primaryStep.equals(step.name()))break;continue;}
                        var table=catalog.required(step.table());Map<String,Object> values=new LinkedHashMap<>();
                        for(var mapping:step.mappings()) {
                            if(!"CONSTANT".equals(mapping.conversion()) && !variables.containsKey(mapping.source())) throw new IllegalArgumentException("来源缺少映射字段: "+mapping.source());
                            Object value="CONSTANT".equals(mapping.conversion())?mapping.constant():variables.get(mapping.source());
                            if(value==null)value=mapping.defaultValue();
                            values.put(mapping.target(),convert(value,mapping));
                        }
                        boolean skip=false;Set<String> missingLookupOutputs=new HashSet<>();
                        if(step.lookups()!=null) for(var lookup:step.lookups()) {
                            Map<String,Object> match=new LinkedHashMap<>();lookup.match().forEach((column,input)->match.put(column,input.startsWith("@")?variables.get(input.substring(1)):values.get(input)));
                            var lookupTable=catalog.required(lookup.table());
                            Map<String,Object> found;
                            try { found=find(lookupTable,match,local,apply); }
                            catch(AmbiguousLookup ex) {
                                if("ERROR".equals(lookup.onMissing()))throw ex;
                                warnings.add(ex.getMessage());found=null;
                            }
                            var resolved=found;
                            if(found==null || deleted(lookupTable,found)) {
                                if("ERROR".equals(lookup.onMissing()))throw new IllegalArgumentException("关联未命中: "+lookup.table());
                                warnings.add("关联未命中: "+lookup.table());
                                missingLookupOutputs.addAll(lookup.outputs().keySet());
                                if("SKIP_STEP".equals(lookup.onMissing()))skip=true;
                            } else lookup.outputs().forEach((output,column)->values.put(output,resolved.get(column)));
                        }
                        if(skip) {if(primaryStep.equals(step.name()))break;continue;}
                        Map<String,Object> keys=new LinkedHashMap<>();step.keys().forEach(k->keys.put(k,values.get(k)));
                        if(keys.values().stream().anyMatch(v->!present(v))) {
                            if("UPDATE_ONLY".equals(step.mode())) {warnings.add("关联业务键缺失: "+step.table());if(primaryStep.equals(step.name()))break;continue;}
                            throw new IllegalArgumentException("业务唯一键缺失: "+step.table());
                        }
                        Map<String,Object> old=find(table,keys,local,apply);
                        if(old!=null && deleted(table,old))throw new Conflict("目标已删除，禁止同步恢复: "+step.table());
                        if(old!=null && !table.immutable() && missingLookupOutputs.stream().anyMatch(column->old.get(column)!=null))throw new Conflict("已有关系无法重新解析，保留原记录: "+step.table());
                        if(old!=null && "INSERT_ONLY".equals(step.mode()))throw new Conflict("目标已存在: "+step.table());
                        if(old==null && "UPDATE_ONLY".equals(step.mode())) {warnings.add("更新目标未找到: "+step.table());if(primaryStep.equals(step.name()))break;continue;}
                        // Existing records from another authority may only be referenced, never adopted.
                        if(old!=null && table.ownerColumn()!=null && values.containsKey(table.ownerColumn()) && !equal(old.get(table.ownerColumn()),values.get(table.ownerColumn())) && !"INSERT_IGNORE".equals(step.mode())) throw new Conflict("目标属于其他来源: "+step.table());
                        Map<String,Object> next=new LinkedHashMap<>(old==null?Map.of():old);
                        Map<String,Object> delta=new LinkedHashMap<>();
                        if(old==null) {
                            values.forEach((k,v)->{if(v!=null || "OVERWRITE".equals(step.nullPolicy()))next.put(k,v);});
                            next.putAll(table.insertDefaults());next.put("id",apply?IdWorker.getId():previewId--);next.put("tenant_id",tenant);
                            next.put("creator","data_sync");next.put("updater","data_sync");next.put("create_time",LocalDateTime.now());next.put("update_time",LocalDateTime.now());next.put("deleted",false);
                            if(table.versioned())next.put("version",0);
                            store.validateValues(schemas.get(table.table()),next,true);
                            rowWrites.add(new Mutation(table,null,next));action="CREATED";
                        } else if(!"INSERT_IGNORE".equals(step.mode())) {
                            boolean newer=step.newerBy()==null || isNewer(values.get(step.newerBy()),old.get(step.newerBy()))
                                    || step.tieBreaker()!=null && values.get(step.newerBy())!=null && equal(values.get(step.newerBy()),old.get(step.newerBy()))
                                    && greater(values.get(step.tieBreaker()),old.get(step.tieBreaker()));
                            for(var col:step.updateColumns()) {
                                Object value=values.get(col);
                                if(newer && (value!=null || "OVERWRITE".equals(step.nullPolicy())) && !equal(old.get(col),value))delta.put(col,value);
                            }
                            if(table.immutable()) {
                                for(var pair:values.entrySet()) if((table.immutableIgnoreColumns()==null || !table.immutableIgnoreColumns().contains(pair.getKey())) && !equal(old.get(pair.getKey()),pair.getValue()))throw new Conflict("不可变历史与来源不一致: "+step.table()+"."+pair.getKey());
                            }
                            if(!delta.isEmpty()) {
                                delta.put("updater","data_sync");delta.put("update_time",LocalDateTime.now());
                                store.validateValues(schemas.get(table.table()),delta,false);
                                rowWrites.add(new Mutation(table,old,delta));next.putAll(delta);
                                if(table.versioned())next.put("version",((Number)old.get("version")).intValue()+1);
                                if(!"CREATED".equals(action))action="UPDATED";
                            }
                        }
                        for(var column:values.keySet()) {beforeValues.put(step.name()+"."+column,old==null?null:old.get(column));afterValues.put(step.name()+"."+column,next.get(column));}
                        long targetId=((Number)next.get("id")).longValue();
                        if(primaryStep.equals(step.name()))primary=targetId;
                        variables.put("$"+step.name()+".id",targetId);
                        local.put(cacheKey(table,keys),next);
                        refs.add(Map.of("context",table.context(),"table",table.table(),"id",targetId,"step",step.name()));
                    }
                    if(primary==null) {
                        // No orphan writes or fictitious lineage when the required primary step is skipped.
                        rowWrites.clear();refs.clear();local=new HashMap<>(overlay);beforeValues.clear();afterValues.clear();
                        warnings.add("主目标未生成，保留来源并跳过整行");
                    }
                    if(row.targetId()!=null && primary!=null && !row.targetId().equals(primary))throw new Conflict("来源已经绑定另一目标，禁止改换业务身份");
                    Map<String,Object> after=new LinkedHashMap<>(row.fields());after.put("_targets",refs);after.putAll(afterValues);
                    if(!warnings.isEmpty())after.put("_warnings",warnings);
                    if(!apply)after.put("_previewVirtualIds",true);
                    changes.add(new Change(row.object(),row.sourceKey(),primary!=null&&primary>0?primary:null,primary==null?"SKIPPED":action,beforeValues,after,warnings.isEmpty()?null:String.join("; ",warnings)));
                    overlay=local;mutations.addAll(rowWrites);
                } catch(IllegalArgumentException ex) {
                    changes.add(new Change(row.object(),row.sourceKey(),null,ex instanceof Conflict?"CONFLICT":"ISSUE",Map.of(),row.fields(),ex.getMessage()));
                }
            }
            if(apply && changes.stream().noneMatch(c->"CONFLICT".equals(c.action()))) {
                for(var mutation:mutations) {
                    if(mutation.before()==null)store.insert(mutation.table(),mutation.values());else store.update(mutation.table(),mutation.before(),mutation.values());
                }
                if (!mutations.isEmpty()) events.publishEvent(new cn.iocoder.yudao.module.pms.integration.api.sync.GenericSyncTargetsChanged(
                        tenant, mutations.stream().map(m -> new cn.iocoder.yudao.module.pms.integration.api.sync.GenericSyncTargetsChanged.Target(
                                m.table().table(),m.before()==null ? Map.of() : m.before(),m.values())).toList()));
            }
            return changes;
        }
    }
    private Map<String,Object> find(GenericTargetCatalog.Table table,Map<String,Object> values,Map<String,Map<String,Object>> overlay,boolean lock) {
        String key=cacheKey(table,values);if(overlay.containsKey(key))return overlay.get(key);
        var rows=store.find(new GenericSyncJdbcStore.Match(table,values,lock));
        if(rows.size()>1)throw new AmbiguousLookup("关联不唯一: "+table.table());
        var result=rows.isEmpty()?null:rows.getFirst();overlay.put(key,result);return result;
    }
    private static String cacheKey(GenericTargetCatalog.Table table,Map<String,Object> values) {return table.table()+":"+JsonUtils.toJsonString(new TreeMap<>(values));}
    private static boolean deleted(GenericTargetCatalog.Table table,Map<String,Object> row) {return table.deletedColumn()!=null && (Boolean.TRUE.equals(row.get(table.deletedColumn())) || "1".equals(Objects.toString(row.get(table.deletedColumn()),"")));}
    private static boolean present(Object value) {return value!=null && (!(value instanceof String text)||!text.isBlank());}
    private static boolean equal(Object a,Object b) {
        if(a==null||b==null)return a==b;
        if(a instanceof Number && b instanceof Number)return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0;
        if(a instanceof java.sql.Timestamp t)a=t.toLocalDateTime();if(b instanceof java.sql.Timestamp t)b=t.toLocalDateTime();
        if(a instanceof java.sql.Date t)a=t.toLocalDate();if(b instanceof java.sql.Date t)b=t.toLocalDate();
        return Objects.equals(a,b)||a.toString().equals(b.toString());
    }
    private static boolean greater(Object incoming,Object old) {return incoming!=null && (old==null || new BigDecimal(incoming.toString()).compareTo(new BigDecimal(old.toString()))>0);}
    private static boolean isNewer(Object incoming,Object old) {return incoming!=null && (old==null || time(incoming).isAfter(time(old)));}
    private static LocalDateTime time(Object value) {
        if(value instanceof Number n)return Instant.ofEpochMilli(n.longValue()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ','T'));
    }
    private static Object convert(Object value,SyncDefinition.Mapping mapping) {
        if(Set.of("ENUM","LOOKUP").contains(mapping.conversion())) {
            String key=value==null?"NULL":value.toString();
            if(!mapping.values().containsKey(key)&&"ENUM".equals(mapping.conversion()))throw new IllegalArgumentException("枚举未定义: "+mapping.target());
            return mapping.values().get(key);
        }
        if(value==null)return null;
        try {return switch(mapping.conversion()) {
            case "DIRECT","CONSTANT" -> value;
            case "STRING" -> value.toString();case "TRIM" -> value.toString().trim();
            case "LONG" -> new BigDecimal(value.toString()).longValueExact();case "DECIMAL" -> new BigDecimal(value.toString());
            case "DATETIME" -> time(value);case "DATE" -> value instanceof Number?time(value).toLocalDate():LocalDate.parse(value.toString().substring(0,10));
            case "JSON" -> JsonUtils.toJsonString(value);
            case "BOOLEAN" -> {if(!Set.of("true","false","0","1").contains(value.toString()))throw new IllegalArgumentException();yield Set.of("true","1").contains(value.toString());}
            default -> throw new IllegalArgumentException();
        };}catch(RuntimeException e){throw new IllegalArgumentException("字段转换失败: "+mapping.target());}
    }
    private record Mutation(GenericTargetCatalog.Table table,Map<String,Object> before,Map<String,Object> values) {}
    private static class AmbiguousLookup extends IllegalArgumentException {AmbiguousLookup(String message){super(message);}}
    private static class Conflict extends IllegalArgumentException {Conflict(String message){super(message);}}
}
