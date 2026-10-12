package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.*;

/** Explicit corrective runs; ordinary account synchronization retains its strict drift guard. */
@Component @RequiredArgsConstructor
public class DppmsUserAuditCorrectionAdapter implements DataSyncAdapter {
    public static final String ADAPTER="DPPMS_USER_AUDIT_CORRECTION";
    static final String PROOF="migrationAuditCorrection";
    record Proof(Long originalTaskId,Long originalRunId,Long targetId,String generatedUpdater,
                 String expectedFieldsJson,String expectedSourceFieldsJson,Long previousCorrectionRunId,String expectedCurrentFieldsJson) {
        Proof(Long taskId,Long runId,Long targetId,String updater,String fields,String source) {
            this(taskId,runId,targetId,updater,fields,source,null,null);
        }
    }
    private final SystemUserMigrationApi users;
    private final SyncTaskService tasks;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    @Override public Descriptor descriptor() {
        var columns=new ArrayList<>(DppmsUserSyncTemplate.COLUMNS.get("fnd_user_info"));columns.add(PROOF);
        return new Descriptor(ADAPTER,"DPPMS已裁决历史更新人纠正",List.of(new ObjectDescriptor("fnd_user_info","原账号及明确纠正证据",
                columns.stream().map(c->new Field(c,c,"STRING",false)).toList(),"SYSTEM","User","system_users",false)),
                List.of("RETAIN"),List.of("UPSERT"),false);
    }
    @Override public boolean sharesTargetAcrossSources(){return true;}
    @Override public void refreshCaches(){}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        if(!batch.full() || !batch.adoptExisting() || batch.clearBeforeLoad() || !"RETAIN".equals(batch.missingPolicy())
                || !"UPSERT".equals(batch.loadingMode()) || batch.rows().isEmpty())
            throw new IllegalArgumentException("历史更新人纠正必须是明确范围、受控接管的整批纠正，禁止清空或分块");
        long tenant=TenantContextHolder.getRequiredTenantId();
        String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))
            throw new IllegalArgumentException("历史更新人纠正所有权失效");
        var task=tasks.required(SyncFieldMapper.primaryKey(owner[2]));
        if(!ADAPTER.equals(task.getAdapter()) || !"DPPMS".equals(task.getSourceSystem()) || task.getActiveRunId()==null)
            throw new IllegalArgumentException("历史更新人纠正任务失效");
        var run=runs.selectScoped(new SyncQueries.Id(tenant,task.getActiveRunId()));
        if(run==null || !Objects.equals(run.getTenantId(),tenant) || !Objects.equals(run.getTaskId(),task.getId())
                || !Objects.equals(run.getConfigVersion(),task.getVersion()) || !Objects.equals(run.getPreview(),!apply)
                || !(apply?"APPLYING":"VALIDATING").equals(run.getStatus()))
            throw new IllegalArgumentException("历史更新人纠正运行身份、阶段或版本失效");
        Map<String,Proof> proofs=new LinkedHashMap<>();
        for(var row:batch.rows()) {
            if(!"fnd_user_info".equals(row.object()) || !row.fields().keySet().containsAll(DppmsUserSyncTemplate.COLUMNS.get(row.object()))
                    || !Objects.equals(row.sourceKey(),Objects.toString(row.fields().get("id"),null))
                    || !(row.fields().get(PROOF) instanceof String text) || text.isBlank() || row.fields().get("updateBy")!=null)
                throw new IllegalArgumentException("纠正原账号字段、来源身份、原NULL更新人或明确证据缺失");
            var proof=JsonUtils.parseObject(text,Proof.class);
            if(proof==null || proof.originalTaskId()==null || proof.originalRunId()==null || proof.targetId()==null
                    || proof.targetId()<=0 || proof.expectedFieldsJson()==null || proof.expectedSourceFieldsJson()==null
                    || proof.generatedUpdater()==null || proof.generatedUpdater().isBlank()
                    || row.targetId()!=null && !Objects.equals(row.targetId(),proof.targetId()) || proofs.put(row.sourceKey(),proof)!=null)
                throw new IllegalArgumentException("纠正目标或原运行证据无效/重复");
            var expected=JsonUtils.parseMap(proof.expectedSourceFieldsJson());
            for(String column:DppmsUserSyncTemplate.COLUMNS.get(row.object())) {
                if(!expected.containsKey(column))throw new IllegalArgumentException("纠正证据未包括全部原字段");
                var actual=row.fields().get(column);var previous=expected.get(column);
                boolean date=Set.of("createTime","updateTime","effectiveFrom","effectiveTo","pwdoverdue").contains(column);
                if(!Objects.equals(date && actual!=null?SyncFieldMapper.sourceTime(actual):Objects.toString(actual,null),
                        date && previous!=null?SyncFieldMapper.sourceTime(previous):Objects.toString(previous,null)))
                    throw new IllegalArgumentException("原账号在裁决后变化，停止纠正");
            }
        }
        var first=proofs.values().iterator().next();
        if(proofs.values().stream().anyMatch(p->!Objects.equals(p.originalTaskId(),first.originalTaskId())
                || !Objects.equals(p.originalRunId(),first.originalRunId()) || !Objects.equals(p.generatedUpdater(),first.generatedUpdater())
                || !Objects.equals(p.previousCorrectionRunId(),first.previousCorrectionRunId())))
            throw new IllegalArgumentException("纠正不能混合不同原任务、运行或生成操作者");
        var original=tasks.required(first.originalTaskId());
        var receipt=runs.selectScoped(new SyncQueries.Id(tenant,first.originalRunId()));
        if(!"DPPMS_USERS".equals(original.getAdapter()) || !"DPPMS".equals(original.getSourceSystem())
                || !Objects.equals(original.getConnectionId(),task.getConnectionId()) || original.getActiveRunId()!=null
                || receipt==null || !Objects.equals(receipt.getTenantId(),tenant) || !Objects.equals(receipt.getTaskId(),original.getId())
                || !Boolean.FALSE.equals(receipt.getPreview()) || !"SUCCESS".equals(receipt.getStatus())
                || !Objects.equals(receipt.getCreator(),first.generatedUpdater()))
            throw new IllegalArgumentException("原用户迁入运行或同源连接无法核实");
        java.time.LocalDateTime generatedFrom=null,generatedTo=null;
        Map<String,cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO> correctedBindings=new HashMap<>();
        if(first.previousCorrectionRunId()!=null) {
            var previous=runs.selectScoped(new SyncQueries.Id(tenant,first.previousCorrectionRunId()));
            var previousTask=previous==null?null:tasks.required(previous.getTaskId());
            if(previous==null || previousTask==null || !ADAPTER.equals(previousTask.getAdapter())
                    || !Objects.equals(previousTask.getConnectionId(),task.getConnectionId()) || !Objects.equals(previous.getTenantId(),tenant)
                    || !Boolean.FALSE.equals(previous.getPreview()) || !"SUCCESS".equals(previous.getStatus())
                    || previous.getStartedAt()==null || previous.getFinishedAt()==null)
                throw new IllegalArgumentException("生成更新时间的原纠正运行无法核实");
            generatedFrom=previous.getStartedAt().withNano(0);generatedTo=previous.getFinishedAt().withNano(0);
            for(var binding:bindings.selectSourceKeys(new SyncQueries.SourceKeys(tenant,previousTask.getId(),"fnd_user_info","fnd_user_info",List.copyOf(proofs.keySet()))))
                if(correctedBindings.put(binding.getSourceKey(),binding)!=null)throw new IllegalArgumentException("原纠正来源绑定重复");
        }
        var sourceBindings=bindings.selectSourceKeys(new SyncQueries.SourceKeys(tenant,original.getId(),"fnd_user_info","fnd_user_info",List.copyOf(proofs.keySet())));
        Map<String,cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO> byKey=new HashMap<>();
        for(var binding:sourceBindings) if(byKey.put(binding.getSourceKey(),binding)!=null)throw new IllegalArgumentException("原账号来源绑定重复");
        var corrections=new ArrayList<AuditCorrection>();var targetKeys=new HashMap<Long,String>();
        for(var pair:proofs.entrySet()) {
            var proof=pair.getValue();var binding=byKey.get(pair.getKey());
            if(binding==null || !Objects.equals(binding.getTargetId(),proof.targetId()) || !Objects.equals(binding.getLastRunId(),proof.originalRunId())
                    || !Objects.equals(JsonUtils.parseMap(binding.getFieldsJson()),JsonUtils.parseMap(proof.expectedFieldsJson())))
                throw new IllegalArgumentException("原绑定目标、完整业务审计或原运行已变化，停止纠正");
            var restored=DppmsUserSyncAdapter.lastApplied(new Binding("fnd_user_info",pair.getKey(),proof.targetId(),JsonUtils.parseMap(proof.expectedFieldsJson())));
            if(restored.audit().updater()!=null || targetKeys.put(restored.id(),pair.getKey())!=null)
                throw new IllegalArgumentException("纠正不是原NULL更新人或正式目标重复");
            var audit=restored.audit();var before=new User(restored.id(),restored.profile(),new Audit(audit.creator(),audit.createTime(),first.generatedUpdater(),audit.updateTime()));
            if(first.previousCorrectionRunId()!=null) {
                var corrected=correctedBindings.get(pair.getKey());
                if(corrected==null || !Objects.equals(corrected.getTargetId(),proof.targetId()) || proof.expectedCurrentFieldsJson()==null
                        || !Objects.equals(DppmsUserSyncAdapter.lastApplied(new Binding("fnd_user_info",pair.getKey(),corrected.getTargetId(),
                            JsonUtils.parseMap(corrected.getFieldsJson()))),restored))
                    throw new IllegalArgumentException("生成更新时间纠正没有原成功纠正的完整目标证据");
                var boundRun=runs.selectScoped(new SyncQueries.Id(tenant,corrected.getLastRunId()));
                if(boundRun==null || !Objects.equals(boundRun.getTaskId(),corrected.getTaskId()) || !"SUCCESS".equals(boundRun.getStatus())
                        || !Boolean.FALSE.equals(boundRun.getPreview()))throw new IllegalArgumentException("原纠正绑定运行失效");
                before=DppmsUserSyncAdapter.lastApplied(new Binding("fnd_user_info",pair.getKey(),proof.targetId(),JsonUtils.parseMap(proof.expectedCurrentFieldsJson())));
            } else if(proof.expectedCurrentFieldsJson()!=null)throw new IllegalArgumentException("明确前值没有原成功纠正运行依据");
            corrections.add(new AuditCorrection(before,restored));
        }
        var request=new AuditCorrectionRequest(tenant,batch.owner(),first.generatedUpdater(),corrections,generatedFrom,generatedTo);
        return (apply?users.applyAuditCorrection(request):users.previewAuditCorrection(request)).stream().map(result->
                new Change("fnd_user_info",targetKeys.get(result.after().id()),result.after().id(),result.action(),
                        DppmsUserSyncAdapter.fields(result.before()),DppmsUserSyncAdapter.fields(result.after()),
                        "按明确裁决仅恢复原NULL更新人；原运行"+first.originalRunId()+"及来源证据保留，其他属性不变")).toList();
    }
}
