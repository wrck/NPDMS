package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi;
import cn.iocoder.yudao.module.pms.project.api.migration.ProjectMasterMigrationApi.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsProjectMasterSyncTemplate.*;

/** Uses the migration/sync engine, existing user bindings and formal PROJ Owner; no project-table access. */
@Component
@RequiredArgsConstructor
public class DppmsProjectMasterSyncAdapter implements DataSyncAdapter {
    record RestorationProof(Long retiredBindingId,Long creationRunId,Long lastRunId,Long targetId,String expectedFingerprint) {}
    private final ProjectMasterMigrationApi projects;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    private final DppmsProjectIdentitySyncAdapter identities;
    @Override public boolean supportsStreaming() { return true; }
    @Override public boolean requiresAllBindings() { return false; }
    @Override public boolean restoresSourceBindings() { return true; }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public void refreshCaches() {}
    @Override public Descriptor descriptor() {
        var fields=new ArrayList<Field>();
        ORIGINAL_COLUMNS.forEach(c->fields.add(new Field(c,c,"STRING",false)));
        ACTOR_COLUMNS.forEach(c->fields.add(new Field(c,c,"STRING",false)));
        ACTOR_REFERENCE_COLUMNS.forEach(c->fields.add(new Field(c,c,"STRING",false)));
        fields.add(new Field("migrationCompanyCode","按原公司外键解析的公司码","STRING",false));
        fields.add(new Field("expectedProjectVersion","已复核项目版本","LONG",false));
        fields.add(new Field("migrationCodeAllocation","已复核跨类型编码尾号分配","OBJECT",false));
        fields.add(new Field("migrationRestoration","获准恢复项目的原回执与全字段保护","OBJECT",false));
        fields.add(new Field("creatorHistoricalCode","获准保留的未关联创建人编码","STRING",false));
        fields.add(new Field("updaterHistoricalCode","获准保留的未关联更新人编码","STRING",false));
        return new Descriptor(ADAPTER,"DPPMS有效项目主档与来源身份",List.of(new ObjectDescriptor(OBJECT,"项目主表原37列",
                fields,"PROJ","Project","proj_project",false), identities.descriptor().objects().getFirst()),
                List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch) { return execute(batch,false); }
    @Override public List<Change> apply(Batch batch) { return execute(batch,true); }

    private List<Change> execute(Batch batch,boolean apply) {
        if(batch==null) throw new IllegalArgumentException("缺少项目迁移批次");
        var aliasRows=batch.rows().stream().filter(r->DppmsProjectIdentitySyncTemplate.OBJECT.equals(r.object())).toList();
        var masters=executeMasters(new Batch(batch.owner(),batch.rows().stream().filter(r->!DppmsProjectIdentitySyncTemplate.OBJECT.equals(r.object())).toList(),
                batch.bindings().stream().filter(b->!DppmsProjectIdentitySyncTemplate.OBJECT.equals(b.object())).toList(),
                batch.full(),batch.missingPolicy(),batch.adoptExisting(),batch.loadingMode(),batch.clearBeforeLoad()),apply);
        if(aliasRows.isEmpty())return masters;
        var result=new ArrayList<>(masters);
        result.addAll(identities.consolidated(new DppmsProjectIdentitySyncAdapter.Consolidated(
                new Batch(batch.owner(),aliasRows,batch.bindings().stream().filter(b->DppmsProjectIdentitySyncTemplate.OBJECT.equals(b.object())).toList(),
                        batch.full(),batch.missingPolicy(),false,batch.loadingMode(),batch.clearBeforeLoad()),apply,masters)));
        return List.copyOf(result);
    }
    private List<Change> executeMasters(Batch batch,boolean apply) {
        long tenant=TenantContextHolder.getRequiredTenantId();
        if (batch==null || batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy())) throw new IllegalArgumentException("项目禁止清空、删除缺项或重置绑定");
        String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if (owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))
            throw new IllegalArgumentException("项目来源所有权无效");
        var task=tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        var definition=JsonUtils.parseObject(task.getDefinition(),SyncDefinition.class);
        if (!Objects.equals(task.getTenantId(),tenant) || !ADAPTER.equals(task.getAdapter())
                || !"DPPMS".equals(task.getSourceSystem()) || definition==null || !"ONCE".equals(definition.mode())
                || definition.resetMappingsBeforeLoad() || task.getActiveRunId()==null)
            throw new IllegalArgumentException("项目仅允许已复核的基线迁移；不得自动接管当前修改");
        var run=runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if (run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(run.getStatus()))
            throw new IllegalArgumentException("项目迁移运行身份或配置版本失效");
        Map<String,Binding> previous=new HashMap<>();
        for (var binding:batch.bindings()) if (previous.put(binding.object()+":"+binding.sourceKey(),binding)!=null)
            throw new IllegalArgumentException("项目来源绑定重复");
        Map<String,Long> actors=resolveActors(batch,tenant,task.getConnectionId());
        var restorationKeys=batch.rows().stream().filter(r->r.fields().get("migrationRestoration")!=null
                && !previous.containsKey(OBJECT+":"+r.sourceKey())).map(Row::sourceKey).toList();
        Map<String,cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO> retired=new HashMap<>();
        if (!restorationKeys.isEmpty()) for (var binding:bindings.selectRetiredSourceKeysForUpdate(
                new SyncQueries.SourceKeys(tenant,task.getId(),OBJECT,OBJECT,restorationKeys)))
            if (retired.put(binding.getSourceKey(),binding)!=null)
                throw new IllegalArgumentException("项目存在多个撤回绑定，不能任选恢复身份");
        Map<Long,cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncRunDO> receipts=new HashMap<>();
        var entries=new ArrayList<Entry>();
        Map<String,Map<String,String>> originals=new LinkedHashMap<>();
        for (var row:batch.rows()) {
            if (!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(ORIGINAL_COLUMNS)
                    || !Objects.equals(row.sourceKey(),text(row.fields(),"projectId")) || originals.containsKey(row.sourceKey())
                    || row.fields().containsKey("_sourcePrimaryKey"))
                throw new IllegalArgumentException("项目原37列、原键或唯一行身份缺失；原ID不得作为正式主键");
            SyncFieldMapper.primaryKey(row.sourceKey());
            Map<String,String> original=original(row.fields()); originals.put(row.sourceKey(),original);
            var prior=previous.get(OBJECT+":"+row.sourceKey());
            if (row.targetId()!=null && prior==null || prior!=null && !Objects.equals(prior.targetId(),row.targetId()))
                throw new IllegalArgumentException("项目目标与正式来源绑定不一致");
            Facts last=null;Restoration restoration=null;Long target=row.targetId();
            if (prior!=null) {
                if (!(prior.lastFields().get("originalFields") instanceof Map<?,?> saved)
                        || !sameOriginal(saved,original) || !(prior.lastFields().get("project") instanceof Map<?,?> facts))
                    throw new IllegalArgumentException("项目原内容或绑定缺失，禁止解释为本轮自动纠正");
                last=decode(facts);
            }
            var fields=row.fields();
            if (prior==null && fields.get("migrationRestoration")!=null) {
                var proof=JsonUtils.parseObject(JsonUtils.toJsonString(fields.get("migrationRestoration")),RestorationProof.class);
                var old=retired.get(row.sourceKey());
                if (proof==null || proof.creationRunId()==null || proof.lastRunId()==null || old==null
                        || !Objects.equals(old.getId(),proof.retiredBindingId())
                        || !Objects.equals(old.getTargetId(),proof.targetId()) || !Objects.equals(old.getLastRunId(),proof.lastRunId()))
                    throw new IllegalArgumentException("项目原撤回绑定、目标或成功回执不匹配");
                for (Long receiptId:List.of(proof.creationRunId(),proof.lastRunId())) {
                    var receipt=receipts.computeIfAbsent(receiptId,id->runs.selectScoped(new SyncQueries.Id(tenant,id)));
                    if (receipt==null || !Objects.equals(receipt.getTaskId(),task.getId())
                            || !Objects.equals(receipt.getTenantId(),tenant) || !Boolean.FALSE.equals(receipt.getPreview())
                            || !"SUCCESS".equals(receipt.getStatus()))
                        throw new IllegalArgumentException("项目原创建或重放回执无效，禁止恢复");
                }
                var created=receipts.get(proof.creationRunId());
                var summary=created.getSummaryJson()==null?Map.<String,Object>of():JsonUtils.parseMap(created.getSummaryJson());
                if (!(summary.get("CREATED") instanceof Number count) || count.longValue()<=0)
                    throw new IllegalArgumentException("项目恢复缺少成功创建回执");
                var saved=JsonUtils.parseMap(old.getFieldsJson());
                if (!(saved.get("originalFields") instanceof Map<?,?> sourceFields) || !sameOriginal(sourceFields,original)
                        || !(saved.get("project") instanceof Map<?,?> facts))
                    throw new IllegalArgumentException("项目完整原字段或原承接值已变化，禁止恢复");
                last=decode(facts);target=proof.targetId();
                if (!Objects.equals(last.id(),target) || !Objects.equals(last.tenantId(),tenant))
                    throw new IllegalArgumentException("项目撤回绑定的业务身份不一致");
                restoration=new Restoration(proof.expectedFingerprint());
            }
            entries.add(new Entry(row.sourceKey(),target,number(fields.get("expectedProjectVersion")),original,
                    companyCode(fields),
                    DppmsDepartmentCodeRules.normalize(text(fields,"column001")),
                    actor(fields,"creator","createBy",actors),actor(fields,"updater","updateBy",actors),last,
                    fields.get("migrationCodeAllocation")==null?null:JsonUtils.parseObject(JsonUtils.toJsonString(fields.get("migrationCodeAllocation")),CodeAllocation.class),restoration));
        }
        var request=new Request(tenant,SyncFieldMapper.primaryKey(run.getCreator()),batch.owner(),entries,batch.adoptExisting());
        var result=apply?projects.apply(request):projects.preview(request);
        if (apply) for (var restored:result) {
            var old=retired.get(restored.sourceKey());
            if (old==null) continue;
            if (!"UPDATED".equals(restored.action()) || restored.after()==null || !Objects.equals(restored.after().id(),old.getTargetId())
                    || bindings.restoreVerifiedBinding(old)!=1)
                throw new IllegalStateException("项目已核验来源绑定恢复冲突，当前块回滚");
        }
        return result.stream().map(r->new Change(OBJECT,r.sourceKey(),r.after()==null?(r.before()==null?null:r.before().id()):r.after().id(),r.action(),
                encode(r.before()),r.after()==null?Map.of():Map.of("project",encode(r.after()),
                "originalFields",evidence(originals.get(r.sourceKey()))),r.message())).toList();
    }
    private static String companyCode(Map<String,Object> fields) {
        String original=text(fields,"compId"),code=text(fields,"migrationCompanyCode");
        if (original==null) {
            if (code!=null) throw new IllegalArgumentException("原空公司不能猜配公司编码");
            return null;
        }
        return DppmsCompanyCodeConverter.fromSource(code);
    }
    private Map<String,Long> resolveActors(Batch batch,long tenant,Long connectionId) {
        Map<String,Set<String>> selected=new HashMap<>();
        for (var row:batch.rows()) for (String kind:List.of("creator","updater")) {
            var fields=row.fields();String name=text(fields,"creator".equals(kind)?"createBy":"updateBy");
            if (name==null || name.isEmpty()) continue;
            if(historicalCode(fields,kind,name)!=null) continue;
            String fnd=text(fields,kind+"FndSourceKey"),t=text(fields,kind+"TSourceKey");
            Long fndCount=number(fields.get(kind+"FndMatchCount")),tCount=number(fields.get(kind+"TMatchCount"));
            if (fndCount==null || tCount==null || fndCount<0 || tCount<0 || fndCount==0 && tCount>1
                    || fndCount==1 && fnd==null || fndCount==0 && fnd!=null || tCount==1 && t==null || tCount==0 && t!=null
                    || fndCount==0 && t==null)
                throw new IllegalArgumentException("项目原审计人员身份缺失或同名未裁决："+name);
            if(fndCount>0) selected.computeIfAbsent("fnd_user_info",ignored->new HashSet<>()).addAll(fndKeys(fields,kind));
            else selected.computeIfAbsent("t_user",ignored->new HashSet<>()).add(t);
        }
        Map<String,Long> result=new HashMap<>();
        for (var group:selected.entrySet()) {
            for (var binding:bindings.selectIdentitySources(new SyncQueries.IdentitySources(tenant,"DPPMS",connectionId,
                    "DPPMS_USERS",group.getKey(),List.copyOf(group.getValue())).originalLine())) {
                String key=group.getKey()+":"+binding.getSourceKey();
                if (binding.getTargetId()==null) throw new IllegalArgumentException("项目审计账号无正式用户绑定");
                Long previous=result.putIfAbsent(key,binding.getTargetId());
                if (previous!=null && !previous.equals(binding.getTargetId())) throw new IllegalArgumentException("项目审计账号绑定多个人员");
            }
            for (String key:group.getValue()) if (!result.containsKey(group.getKey()+":"+key))
                throw new IllegalArgumentException("先完成用户迁移及来源绑定，再承接项目审计");
        }
        for(var row:batch.rows()) for(String kind:List.of("creator","updater")) {
            if(historicalCode(row.fields(),kind,text(row.fields(),"creator".equals(kind)?"createBy":"updateBy"))!=null) continue;
            Long count=number(row.fields().get(kind+"FndMatchCount"));
            if(count!=null && count>1) {
                Set<Long> targets=new HashSet<>();for(String key:fndKeys(row.fields(),kind))targets.add(result.get("fnd_user_info:"+key));
                if(targets.size()!=1 || targets.contains(null)) throw new IllegalArgumentException("同名来源审计账号尚未全部绑定同一正式用户");
            }
        }
        return result;
    }
    private static String actor(Map<String,Object> fields,String kind,String raw,Map<String,Long> actors) {
        String name=text(fields,raw);if (name==null || name.isEmpty()) return name;
        String historical=historicalCode(fields,kind,name);if(historical!=null)return historical;
        Long count=number(fields.get(kind+"FndMatchCount"));
        String fnd=count!=null && count>0?fndKeys(fields,kind).getFirst():null;
        return actors.get(fnd==null?"t_user:"+text(fields,kind+"TSourceKey"):"fnd_user_info:"+fnd).toString();
    }
    private static String historicalCode(Map<String,Object> fields,String kind,String original) {
        String value=text(fields,kind+"HistoricalCode");if(value==null)return null;
        if(!Set.of("sys","jiangyating").contains(value) || !Objects.equals(value,original))
            throw new IllegalArgumentException("未经裁决的原审计编码不得代替正式用户引用");
        return value;
    }
    private static List<String> fndKeys(Map<String,Object> fields,String kind) {
        Long count=number(fields.get(kind+"FndMatchCount"));String single=text(fields,kind+"FndSourceKey"),csv=text(fields,kind+"FndSourceKeys");
        var keys=csv==null?(single==null?List.<String>of():List.of(single)):List.of(csv.split(",",-1));
        if(count==null || keys.size()!=count || new HashSet<>(keys).size()!=keys.size()
                || keys.stream().anyMatch(key->!key.matches("[1-9][0-9]*"))
                || count==1 && !Objects.equals(single,keys.getFirst()) || count>1 && single!=null)
            throw new IllegalArgumentException("项目来源审计账号集合不完整或存在未裁决单值选择");
        return keys;
    }
    private static Map<String,String> original(Map<String,Object> fields) {
        Map<String,String> result=new LinkedHashMap<>();
        for (String column:ORIGINAL_COLUMNS) {
            Object value=fields.get(column);
            result.put(column,value==null?null:DATES.contains(column)?SyncFieldMapper.sourceTime(value).toString():value.toString());
        }
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,Object> evidence(Map<String,String> original) {
        Map<String,Object> result=new LinkedHashMap<>();
        original.forEach((key,value)->result.put(key,value==null?tools.jackson.databind.node.NullNode.getInstance():value));
        return result;
    }
    private static boolean sameOriginal(Map<?,?> previous,Map<String,String> incoming) {
        if (previous.size()!=ORIGINAL_COLUMNS.size() || !previous.keySet().containsAll(ORIGINAL_COLUMNS)) return false;
        for (String key:ORIGINAL_COLUMNS) {
            Object value=previous.get(key);
            String saved=value==null || value instanceof tools.jackson.databind.JsonNode n && n.isNull()?null:value.toString();
            if (!Objects.equals(saved,incoming.get(key))) return false;
        }
        return true;
    }
    private static Map<String,Object> encode(Facts facts) {
        if (facts==null) return Map.of();
        Map<String,Object> result=JsonUtils.parseMap(JsonUtils.toJsonString(facts));
        for (String field:List.of("projectStartTime","projectRefreshTime","projectCloseTime","orderCreateTime",
                "effectiveFrom","effectiveTo","createTime","updateTime","closedAt")) {
            Object value=result.get(field);
            if (value!=null) result.put(field,SyncFieldMapper.sourceTime(value).toString());
        }
        return result;
    }
    static Facts decode(Map<?,?> saved) {
        Map<String,Object> fields=new LinkedHashMap<>();saved.forEach((key,value)->fields.put(key.toString(),value));
        Object disabled=fields.get("disabled");
        if (disabled!=null && !(disabled instanceof Boolean)) throw new IllegalArgumentException("项目绑定有效性字段无效");
        return new Facts(number(fields.get("id")),number(fields.get("tenantId")),number(fields.get("version")),
                text(fields,"projectCode"),text(fields,"projectName"),number(fields.get("companyId")),
                text(fields,"companyCode"),text(fields,"companyName"),text(fields,"projectType"),text(fields,"projectCategory"),
                text(fields,"signingMethod"),text(fields,"implementationMode"),text(fields,"customerProjectName"),
                text(fields,"salesType"),text(fields,"majorProjectLevel"),text(fields,"marketName"),text(fields,"systemName"),
                text(fields,"expendName"),text(fields,"industryName"),text(fields,"notTrackReason"),
                time(fields,"projectStartTime"),time(fields,"projectRefreshTime"),time(fields,"projectCloseTime"),time(fields,"orderCreateTime"),
                text(fields,"customInfo"),(Boolean)disabled,time(fields,"effectiveFrom"),time(fields,"effectiveTo"),
                text(fields,"creator"),time(fields,"createTime"),text(fields,"updater"),time(fields,"updateTime"),
                text(fields,"lifecycleStatus"),text(fields,"currentStage"),time(fields,"closedAt"));
    }
    private static java.time.LocalDateTime time(Map<String,Object> fields,String key) {
        Object value=fields.get(key);return value==null?null:SyncFieldMapper.sourceTime(value);
    }
    private static String text(Map<String,Object> fields,String key) { Object value=fields.get(key);return value==null?null:value.toString(); }
    private static Long number(Object value) { return value==null?null:new BigDecimal(value.toString()).longValueExact(); }
}
