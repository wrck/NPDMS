package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceSourceRelationshipApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceSourceRelationshipApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncBindingDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDeviceRelationshipSyncTemplate.*;

/** Approved audit correction only. Ordinary import still rejects any existing audit difference. */
@Component @RequiredArgsConstructor
public class DppmsDeviceRelationshipAuditCorrectionAdapter implements DataSyncAdapter {
    public static final String KEY="DPPMS_DEVICE_RELATIONSHIP_AUDIT_CORRECTION";
    static final String PROOF="auditCorrectionProof",RECEIPT="originalRunProof";
    record Proof(Long targetId,Long bindingId,String targetFingerprint,String bindingFingerprint,String sourceFingerprint) {}
    record RunProof(Long originalTaskId,DeviceRelationshipAuditEvidenceMapper.Receipt receipt) {}
    private final DppmsDeviceSourceRelationshipApi relationships;
    private final SyncTaskService tasks;
    private final DeviceRelationshipAuditEvidenceMapper evidence;
    @Override public boolean supportsStreaming(){return true;}
    @Override public boolean requiresAllBindings(){return false;}
    @Override public boolean sharesTargetAcrossSources(){return true;}
    @Override public void refreshCaches(){}
    @Override public Descriptor descriptor(){
        var columns=new ArrayList<>(ORIGINAL_COLUMNS);columns.addAll(List.of(PROOF,RECEIPT));
        return new Descriptor(KEY,"DPPMS已裁决主附SN历史审计纠正",List.of(new ObjectDescriptor(OBJECT,
                "原8列与逐键裁决保护",columns.stream().map(n->new Field(n,n,"STRING",true)).toList(),
                "AST","DeviceRelationship","ast_device_relationship",false)),List.of("RETAIN"),List.of("UPSERT"),false,true);
    }
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class)
    public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply){
        long tenant=TenantContextHolder.getRequiredTenantId();
        if(batch==null || batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode())
                || !"RETAIN".equals(batch.missingPolicy()) || batch.rows().size()>500) fail("加载方式或块大小无效");
        String[] owner=batch.owner()==null?new String[0]:batch.owner().split(":");
        if(owner.length!=3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1])) fail("运行所有权无效");
        // The 65,406 pinned review proofs are already loaded by the streaming framework.
        // Load only the locked execution metadata here; parsing that large definition again exhausts the heap.
        var task=evidence.selectCorrectionContextForUpdate(new SyncQueries.Id(tenant,SyncFieldMapper.primaryKey(owner[2])));
        if(task==null || !Objects.equals(task.tenantId(),tenant) || !KEY.equals(task.adapter()) || !"DPPMS".equals(task.sourceSystem())
                || !"ONCE".equals(task.mode()) || !Boolean.FALSE.equals(task.resetMappingsBeforeLoad())
                || task.activeRunId()==null || !Objects.equals(task.runTaskId(),task.taskId()) || !Objects.equals(task.runTenantId(),tenant)
                || !Objects.equals(task.runConfigVersion(),task.version()) || !Objects.equals(task.runPreview(),!apply)
                || !(apply?Set.of("READING","APPLYING"):Set.of("READING","VALIDATING")).contains(task.runStatus())) fail("运行、配置或阶段失效");
        var previous=new HashMap<String,Binding>();
        for(var binding:batch.bindings()) if(!OBJECT.equals(binding.object()) || previous.put(binding.sourceKey(),binding)!=null)
            fail("纠正来源绑定重复或对象不符");
        var originals=new LinkedHashMap<String,Map<String,Object>>();var proofs=new LinkedHashMap<String,Proof>();
        RunProof runProof=null;var changes=new ArrayList<Change>();
        for(var row:batch.rows()) {
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().containsAll(ORIGINAL_COLUMNS)
                    || !Objects.equals(row.sourceKey(),text(row.fields().get("id"))) || row.fields().containsKey("_sourcePrimaryKey"))
                fail("来源身份或全部8字段缺失");
            var raw=original(row.fields());if(originals.put(row.sourceKey(),raw)!=null) fail("原键重复");
            var declared=decode(row.fields().get(RECEIPT),RunProof.class);
            if(declared==null || declared.receipt()==null || declared.receipt().id()==null || declared.originalTaskId()==null) fail("原成功回执保护缺失");
            if(runProof==null) runProof=declared; else if(!runProof.equals(declared)) fail("不得混用原创建回执");
            var proof=decode(row.fields().get(PROOF),Proof.class);
            if(proof==null || !digest(proof.sourceFingerprint()) || !sourceFingerprint(raw).equals(proof.sourceFingerprint()))
                fail("原8字段在裁决后改变或原记录不在已裁决范围");
            if(proof.targetId()==null) {
                // These 360 rows were explicitly excluded: do not silently filter them from the original table.
                if(proof.bindingId()!=null || proof.targetFingerprint()!=null || proof.bindingFingerprint()!=null
                        || row.targetId()!=null || previous.containsKey(row.sourceKey())
                        || text(raw.get("sn1"))==null || !Objects.equals(text(raw.get("sn1")),text(raw.get("sn2")))
                        || !Objects.equals(text(raw.get("item1")),text(raw.get("item2")))) fail("原记录不在已裁决范围");
                changes.add(new Change(OBJECT,row.sourceKey(),null,"SKIPPED",Map.of(),Map.of("originalFields",raw),
                        "用户明确不迁入双设备关系：双方SN和产品相同，不构成主附设备关系事实"));
                continue;
            }
            if(proof.targetId()<=0 || proof.bindingId()==null || proof.bindingId()<=0
                    || !digest(proof.targetFingerprint()) || !digest(proof.bindingFingerprint())
                    || !digest(proof.sourceFingerprint()) || !sourceFingerprint(raw).equals(proof.sourceFingerprint()))
                fail("原8字段在裁决后改变或逐键保护无效");
            var prior=previous.get(row.sourceKey());
            if(row.targetId()!=null && (prior==null || !Objects.equals(row.targetId(),proof.targetId()))
                    || prior!=null && (!Objects.equals(prior.targetId(),proof.targetId())
                    || !(prior.lastFields().get("originalFields") instanceof Map<?,?> previousSource)
                    || !sourceFingerprint(previousSource).equals(proof.sourceFingerprint())))
                fail("纠正绑定或原字段发生后续变化");
            proofs.put(row.sourceKey(),proof);
        }
        if(runProof==null) return List.of();
        var originalTask=tasks.locked(runProof.originalTaskId());
        var originalReceipt=evidence.selectReceiptForUpdate(new SyncQueries.Id(tenant,runProof.receipt().id()));
        if(!Objects.equals(originalTask.getTenantId(),tenant) || !"DPPMS".equals(originalTask.getSourceSystem())
                || !"TABLE_MAPPING:device-sn".equals(originalTask.getAdapter()) || originalTask.getActiveRunId()!=null
                || !Objects.equals(originalTask.getConnectionId(),task.connectionId())
                || originalReceipt==null || !runProof.receipt().equals(originalReceipt)
                || !Objects.equals(originalReceipt.taskId(),originalTask.getId()) || !"SUCCESS".equals(originalReceipt.status())
                || !Boolean.FALSE.equals(originalReceipt.preview()) || !Boolean.FALSE.equals(originalReceipt.deleted()))
            fail("原任务正在运行或原成功回执在裁决后改变");
        var bindings=evidence.selectBindingsForUpdate(new SyncQueries.SourceKeys(tenant,originalTask.getId(),
                "DEVICE_RELATION",OBJECT,List.copyOf(proofs.keySet())));
        var originalsByKey=new HashMap<String,SyncBindingDO>();
        for(var binding:bindings) if(originalsByKey.put(binding.getSourceKey(),binding)!=null) fail("原绑定多义");
        var entries=new ArrayList<AuditEntry>();
        for(var pair:proofs.entrySet()) {
            var proof=pair.getValue();var binding=originalsByKey.get(pair.getKey());var raw=originals.get(pair.getKey());
            if(binding==null || !Objects.equals(binding.getId(),proof.bindingId()) || !Objects.equals(binding.getTargetId(),proof.targetId())
                    || !Objects.equals(binding.getLastRunId(),originalReceipt.id()) || !bindingFingerprint(binding).equals(proof.bindingFingerprint()))
                fail("原绑定全字段、目标或成功运行发生变化");
            var saved=JsonUtils.parseMap(binding.getFieldsJson());
            if(!(saved.get("_syncSourcePayload") instanceof Map<?,?> source)
                    || !sourceFingerprint(source).equals(proof.sourceFingerprint())) fail("原绑定未保存一致的8列来源");
            var prior=previous.get(pair.getKey());var last=prior==null?null:decode(prior.lastFields().get("relationship"),AuditRecord.class);
            if(prior!=null && (last==null || !Objects.equals(last.id(),proof.targetId()))) fail("纠正后19列绑定缺失");
            entries.add(new AuditEntry(new Relation(SyncFieldMapper.primaryKey(pair.getKey()),text(raw.get("sn1")),text(raw.get("item1")),
                    text(raw.get("sn2")),text(raw.get("item2")),text(raw.get("contract")),text(raw.get("createtime")),text(raw.get("updatetime"))),
                    proof.targetId(),last==null?proof.targetFingerprint():last.fingerprint()));
        }
        if(!entries.isEmpty()) {
            var request=new AuditRequest(tenant,SyncFieldMapper.primaryKey(task.actorCode()),batch.owner(),entries);
            var results=apply?relationships.applyAuditCorrection(request):relationships.previewAuditCorrection(request);
            if(results.size()!=entries.size() || !new HashSet<>(results.stream().map(AuditResult::sourceKey).toList()).equals(proofs.keySet()))
                fail("AST回执身份或数量不一致");
            for(var result:results) {
                if(result.before()==null || result.after()==null || !Objects.equals(result.after().id(),proofs.get(result.sourceKey()).targetId()))
                    fail("AST回执目标改变");
                changes.add(new Change(OBJECT,result.sourceKey(),result.after().id(),result.action(),encode(result.before()),
                        Map.of("relationship",encode(result.after()),"originalFields",originals.get(result.sourceKey())),
                        "已裁决限定纠正：恢复原历史日期，关系、操作者和有效区间保持，旧回执及绑定保持"));
            }
        }
        return List.copyOf(changes);
    }
    static String bindingFingerprint(SyncBindingDO b) {
        return fingerprint(Arrays.asList(b.getId(),b.getTenantId(),b.getTaskId(),b.getObjectKey(),b.getSourceObject(),
                b.getSourceKey(),b.getTargetId(),b.getFieldsJson(),b.getLastRunId(),b.getCreator(),b.getCreateTime(),
                b.getUpdater(),b.getUpdateTime(),b.getDeleted(),b.getTargetShared()));
    }
    static String sourceFingerprint(Map<?,?> source) {return fingerprint(ORIGINAL_COLUMNS.stream().map(source::get).toList());}
    private static String fingerprint(List<?> values) {
        var canonical=values.stream().map(v->v instanceof LocalDateTime d?d.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")):text(v)).toList();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JsonUtils.toJsonString(canonical).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static boolean digest(String value){return value!=null && value.matches("[0-9a-f]{64}");}
    private static Map<String,Object> original(Map<String,Object> f) {
        var result=new LinkedHashMap<String,Object>();for(String n:ORIGINAL_COLUMNS) result.put(n,text(f.get(n))==null?
                tools.jackson.databind.node.NullNode.getInstance():text(f.get(n)));return result;
    }
    private static Map<String,Object> encode(AuditRecord r) {
        var result=new LinkedHashMap<>(JsonUtils.parseMap(JsonUtils.toJsonString(r)));
        for(String n:List.of("id","tenantId","sourceDeviceSn","targetDeviceSn","sourceItem","targetItem","relationshipType","contractNo",
                "effectiveFrom","effectiveTo","sourceSystem","sourceKey","sourceVersion","version","creator","createTime","updater","updateTime","deleted"))
            result.putIfAbsent(n,tools.jackson.databind.node.NullNode.getInstance());return result;
    }
    private static <T> T decode(Object value,Class<T> type) {return text(value)==null?null:
            JsonUtils.parseObject(value instanceof String s?s:JsonUtils.toJsonString(value),type);}
    private static String text(Object value){return value==null || value instanceof tools.jackson.databind.JsonNode n && n.isNull()?null:value.toString();}
    private static void fail(String reason){throw new IllegalArgumentException("主附SN限定纠正："+reason);}
}
