package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsContractCollectionPlanSyncTemplate.*;

/** Baseline migration uses existing INT transactions and bindings; no technical identity columns in COM. */
@Component
@RequiredArgsConstructor
public class DppmsContractCollectionPlanSyncAdapter implements DataSyncAdapter {
    private final ContractCollectionPlanMigrationApi plans;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    @Override public boolean supportsStreaming() { return true; }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public void refreshCaches() {}
    @Override public Descriptor descriptor() {
        var columns = new ArrayList<>(ORIGINAL_COLUMNS); columns.addAll(ACTOR_COLUMNS); columns.addAll(ACTOR_REFERENCE_COLUMNS);
        var fields = new ArrayList<>(columns.stream().map(name -> new Field(name,name,"STRING",
                Set.of("id","createTime","updateTime").contains(name))).toList());
        fields.add(new Field("existingPlanId","已核实的既有正式计划ID","LONG",false));
        fields.add(new Field("creatorUnlinkedCode","已裁决未关联的原创建人编码","STRING",false));
        fields.add(new Field("updaterUnlinkedCode","已裁决未关联的原更新人编码","STRING",false));
        return new Descriptor(ADAPTER,"DPPMS合同回款计划",List.of(new ObjectDescriptor(OBJECT,"合同回款计划原16列",
                fields,"COM","ContractCollectionPlan","com_contract_collection_plan",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override
    @org.springframework.transaction.annotation.Transactional(isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch) { return execute(batch,false); }
    @Override public List<Change> apply(Batch batch) { return execute(batch,true); }
    private List<Change> execute(Batch batch, boolean apply) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        if (batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("回款计划禁止清空、删除来源缺项或重置绑定");
        String[] owner = batch.owner() == null ? new String[0] : batch.owner().split(":");
        if (owner.length != 3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))
            throw new IllegalArgumentException("回款计划来源所有权无效");
        var task = tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        var definition = JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        if (!Objects.equals(task.getTenantId(),tenant) || !ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || definition == null
                || !"ONCE".equals(definition.mode()) || definition.resetMappingsBeforeLoad() || task.getActiveRunId() == null)
            throw new IllegalArgumentException("回款计划仅允许受控基线迁移；稳定身份确认前禁止跨刷新调度或重置绑定");
        var run = runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if (run == null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion())
                || !Objects.equals(run.getPreview(),!apply)
                || !(apply ? Set.of("READING","APPLYING") : Set.of("READING","VALIDATING")).contains(run.getStatus()))
            throw new IllegalArgumentException("回款计划运行身份或配置版本失效");
        Long actorId = SyncFieldMapper.primaryKey(run.getCreator());
        Map<String,Binding> previous = new HashMap<>();
        for (var binding : batch.bindings()) if (previous.put(binding.object()+":"+binding.sourceKey(),binding) != null)
            throw new IllegalArgumentException("回款计划来源绑定重复");
        Map<String,Long> actors = resolveActors(batch,tenant,task.getConnectionId());
        var entries = new ArrayList<Entry>();
        var raw = new LinkedHashMap<String,Map<String,Object>>();
        for (var row : batch.rows()) {
            if (!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(ORIGINAL_COLUMNS)
                    || !Objects.equals(row.sourceKey(),text(row.fields(),"id")) || raw.put(row.sourceKey(),original(row.fields())) != null)
                throw new IllegalArgumentException("回款计划原16列、原键或唯一行身份缺失");
            SyncFieldMapper.primaryKey(row.sourceKey());
            var prior = previous.get(OBJECT+":"+row.sourceKey());
            if (row.targetId() != null && prior == null || prior != null && !Objects.equals(prior.targetId(),row.targetId()))
                throw new IllegalArgumentException("回款计划目标与当前同步绑定不一致");
            if (prior != null && (!prior.lastFields().containsKey("plan")
                    || !sameOriginal(prior.lastFields().get("originalFields"),raw.get(row.sourceKey()))))
                throw new IllegalArgumentException("回款计划原键内容已变；不能把重建后的自增ID当稳定身份");
            if (row.fields().containsKey("_sourcePrimaryKey")) throw new IllegalArgumentException("原自增ID不得用作正式计划ID");
            Long existing = optionalId(row.fields().get("existingPlanId"));
            if (existing != null && !batch.adoptExisting()) throw new IllegalArgumentException("已有十条计划的接管未批准");
            Plan last = prior == null ? null : decode(prior.lastFields().get("plan"));
            var fields = row.fields();
            Plan incoming = new Plan(null,tenant,null,text(fields,"contractNo"),text(fields,"batchCode"),text(fields,"basicDataName"),
                    text(fields,"referenceEventName"),time(fields,"eventPlanHappenDate"),integer(fields.get("afterDaysNum")),
                    time(fields,"eventActualFinishDate"),text(fields,"marketingFeedback"),text(fields,"dataSource"),
                    time(fields,"effectiveFrom"),time(fields,"effectiveTo"),actor(fields,"creator","createBy",actors),time(fields,"createTime"),
                    actor(fields,"updater","updateBy",actors),time(fields,"updateTime"),false);
            entries.add(new Entry(row.sourceKey(),row.targetId(),existing,last,incoming));
        }
        var request = new Request(tenant,actorId,batch.owner(),entries,batch.adoptExisting());
        return (apply ? plans.apply(request) : plans.preview(request)).stream().map(result -> new Change(OBJECT,result.sourceKey(),
                result.after() == null ? null : result.after().id(),result.action(),encode(result.before()),
                result.after() == null ? Map.of() : Map.of("plan",encode(result.after()),"originalFields",raw.get(result.sourceKey())),
                result.message())).toList();
    }
    private Map<String,Long> resolveActors(Batch batch, long tenant, Long connectionId) {
        Map<String,Set<String>> selected = new HashMap<>();
        for (var row : batch.rows()) for (String kind : List.of("creator","updater")) {
            var fields = row.fields(); String name = text(fields,"creator".equals(kind) ? "createBy" : "updateBy");
            if (name == null || name.isEmpty() || Set.of("CrmInterfaceUser","crmapp").contains(name)) continue;
            String fnd = text(fields,kind+"FndSourceKey"), t = text(fields,kind+"TSourceKey");
            Integer fndCount = integer(fields.get(kind+"FndMatchCount")), tCount = integer(fields.get(kind+"TMatchCount"));
            if (fndCount == null || tCount == null || fndCount < 0 || tCount < 0
                    || fndCount == 0 && tCount > 1 || fndCount == 1 && fnd == null || fndCount == 0 && fnd != null
                    || tCount == 1 && t == null || tCount == 0 && t != null)
                throw new IllegalArgumentException("原审计账号匹配数量缺失或同名未裁决："+name);
            if (fndCount == 0 && t == null) {
                if (approvedUnlinkedActor(fields,kind,name)) continue;
                throw new IllegalArgumentException("原审计账号不存在或同名未裁决："+name);
            }
            if(fndCount>0) selected.computeIfAbsent("fnd_user_info",ignored -> new HashSet<>()).addAll(fndKeys(fields,kind));
            else selected.computeIfAbsent("t_user",ignored -> new HashSet<>()).add(t);
        }
        Map<String,Long> result = new HashMap<>();
        for (var group : selected.entrySet()) {
            for (var binding : bindings.selectIdentitySources(new SyncQueries.IdentitySources(tenant,"DPPMS",connectionId,
                    "DPPMS_USERS",group.getKey(),List.copyOf(group.getValue())))) {
                String key = group.getKey()+":"+binding.getSourceKey();
                if (binding.getTargetId() == null) throw new IllegalArgumentException("原审计账号没有正式用户绑定");
                Long prior = result.putIfAbsent(key,binding.getTargetId());
                if (prior != null && !prior.equals(binding.getTargetId())) throw new IllegalArgumentException("原审计账号绑定多个正式用户");
            }
            for (String key : group.getValue()) if (!result.containsKey(group.getKey()+":"+key))
                throw new IllegalArgumentException("先完成已批准的用户迁移和审计绑定，再承接计划审计");
        }
        for(var row:batch.rows()) for(String kind:List.of("creator","updater")) {
            Integer count=integer(row.fields().get(kind+"FndMatchCount"));
            String name=text(row.fields(),"creator".equals(kind)?"createBy":"updateBy");
            if(name==null || name.isEmpty() || Set.of("CrmInterfaceUser","crmapp").contains(name) || count==null || count<=1)continue;
            Set<Long> targets=new HashSet<>();for(String key:fndKeys(row.fields(),kind))targets.add(result.get("fnd_user_info:"+key));
            if(targets.size()!=1 || targets.contains(null))throw new IllegalArgumentException("同名计划审计账号尚未全部绑定同一正式用户");
        }
        return result;
    }
    private static String actor(Map<String,Object> fields, String kind, String raw, Map<String,Long> actors) {
        String name = text(fields,raw);
        if (name == null || name.isEmpty() || Set.of("CrmInterfaceUser","crmapp").contains(name)) return name;
        Integer count=integer(fields.get(kind+"FndMatchCount"));
        String fnd = count!=null && count>0?fndKeys(fields,kind).getFirst():null;
        if (fnd == null && text(fields,kind+"TSourceKey") == null && approvedUnlinkedActor(fields,kind,name)) return name;
        return Objects.toString(actors.get(fnd == null ? "t_user:"+text(fields,kind+"TSourceKey") : "fnd_user_info:"+fnd),null);
    }
    private static boolean approvedUnlinkedActor(Map<String,Object> fields, String kind, String name) {
        return name != null && !name.isEmpty() && Objects.equals(name,text(fields,kind+"UnlinkedCode"))
                && Integer.valueOf(0).equals(integer(fields.get(kind+"FndMatchCount")))
                && Integer.valueOf(0).equals(integer(fields.get(kind+"TMatchCount")))
                && text(fields,kind+"FndSourceKey") == null && text(fields,kind+"TSourceKey") == null;
    }
    private static List<String> fndKeys(Map<String,Object> fields,String kind) {
        Integer count=integer(fields.get(kind+"FndMatchCount"));
        String single=text(fields,kind+"FndSourceKey"),csv=text(fields,kind+"FndSourceKeys");
        var keys=csv==null?(single==null?List.<String>of():List.of(single)):List.of(csv.split(",",-1));
        if(count==null || keys.size()!=count || new HashSet<>(keys).size()!=keys.size()
                || keys.stream().anyMatch(key->!key.matches("[1-9][0-9]*"))
                || count==1 && !Objects.equals(single,keys.getFirst()) || count>1 && single!=null)
            throw new IllegalArgumentException("计划来源审计账号集合不完整或存在未裁决单值选择");
        return keys;
    }
    private static Map<String,Object> original(Map<String,Object> fields) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (String name : ORIGINAL_COLUMNS) {
            Object value = fields.get(name);
            if (Set.of("eventPlanHappenDate","eventActualFinishDate","createTime","updateTime","effectiveFrom","effectiveTo").contains(name)) {
                var date = value == null ? null : SyncFieldMapper.sourceTime(value); value = date == null ? null : date.toString();
            } else if ("afterDaysNum".equals(name)) { var number = integer(value); value = number == null ? null : number.toString(); }
            else value = value == null ? null : value.toString();
            // Explicit JSON null keeps all sixteen column names under the platform's NON_NULL serialization.
            result.put(name,value == null ? tools.jackson.databind.node.NullNode.getInstance() : value);
        }
        return result;
    }
    private static boolean sameOriginal(Object before, Map<String,Object> after) {
        if (!(before instanceof Map<?,?> fields) || !fields.keySet().containsAll(ORIGINAL_COLUMNS)) return false;
        for (String name : ORIGINAL_COLUMNS) if (!Objects.equals(nullValue(fields.get(name)),nullValue(after.get(name)))) return false;
        return true;
    }
    private static Object nullValue(Object value) {
        return value instanceof tools.jackson.databind.JsonNode node && node.isNull() ? null : value;
    }
    private static Map<String,Object> encode(Plan plan) {
        if (plan == null) return Map.of();
        Map<String,Object> fields = new LinkedHashMap<>(JsonUtils.parseObject(JsonUtils.toJsonString(plan),Map.class));
        fields.put("eventPlannedAt",date(plan.eventPlannedAt()));fields.put("eventActualFinishedAt",date(plan.eventActualFinishedAt()));
        fields.put("effectiveFrom",date(plan.effectiveFrom()));fields.put("effectiveTo",date(plan.effectiveTo()));
        fields.put("createTime",date(plan.createTime()));fields.put("updateTime",date(plan.updateTime()));return fields;
    }
    @SuppressWarnings("unchecked")
    static Plan decode(Object value) {
        if (!(value instanceof Map<?,?>)) throw new IllegalArgumentException("回款计划既有绑定缺少正式字段证据");
        var fields = (Map<String,Object>) value;
        return new Plan(optionalId(fields.get("id")),optionalId(fields.get("tenantId")),optionalId(fields.get("contractId")),
                text(fields,"contractNo"),text(fields,"batchCode"),text(fields,"collectionItemName"),text(fields,"referenceEventName"),
                time(fields,"eventPlannedAt"),integer(fields.get("afterDays")),time(fields,"eventActualFinishedAt"),text(fields,"marketingFeedback"),
                text(fields,"dataSource"),time(fields,"effectiveFrom"),time(fields,"effectiveTo"),text(fields,"creator"),time(fields,"createTime"),
                text(fields,"updater"),time(fields,"updateTime"),Boolean.FALSE.equals(fields.get("deleted")) ? false : true);
    }
    private static String text(Map<String,Object> fields, String key) { return Objects.toString(fields.get(key),null); }
    private static LocalDateTime time(Map<String,Object> fields, String key) { return fields.get(key) == null ? null : SyncFieldMapper.sourceTime(fields.get(key)); }
    private static String date(LocalDateTime value) { return value == null ? null : value.toString(); }
    private static Long optionalId(Object value) { return value == null ? null : SyncFieldMapper.primaryKey(value.toString()); }
    private static Integer integer(Object value) { return value == null ? null : Integer.valueOf(value.toString()); }
}
