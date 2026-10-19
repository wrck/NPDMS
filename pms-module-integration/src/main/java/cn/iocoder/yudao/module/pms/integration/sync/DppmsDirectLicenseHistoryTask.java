package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import cn.iocoder.yudao.module.pms.integration.sync.DppmsSecureLicenseSyncTemplate.Approved;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDirectLicenseHistoryTemplate.*;
/** Independent whole-table history: tenant/run authorization and immutable encrypted facts; no pilot project. */
@Component
public class DppmsDirectLicenseHistoryTask implements DataSyncAdapter {
    private final SyncOwnerRunContext context;
    private final DirectLicenseHistoryPersistence owner;
    public DppmsDirectLicenseHistoryTask(SyncOwnerRunContext context,javax.sql.DataSource source,
            cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectLicenseHistoryMapper records,
            cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions,
            cn.iocoder.yudao.module.system.api.user.AdminUserApi users,
            cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi commands,
            cn.iocoder.yudao.module.pms.platform.api.migration.PlatformMigrationEvidenceApi evidence,
            @org.springframework.beans.factory.annotation.Value("${pms.license-history.master-key:}") String key,
            @org.springframework.beans.factory.annotation.Value("${pms.license-history.key-id:V1}") String keyId) {
        this.context=context;this.records=records;
        this.owner=new DirectLicenseHistoryPersistence(source,records,new DirectLicenseHistoryCipher(key,keyId),permissions,users,commands,evidence);
    }
    private final cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectLicenseHistoryMapper records;
    @Override public boolean requiresAllBindings(){return false;}
    @Override public boolean supportsStreaming(){return true;}
    @Override public Descriptor descriptor(){return new Descriptor(ADAPTER,"DPPMS受控许可历史",List.of(new ObjectDescriptor(OBJECT,"批准许可历史",
        COLUMNS.stream().map(n->new Field(n,n,"STRING",false)).toList(),"AST","DeviceLicenseRecord","ast_device_license_record",false)),List.of("RETAIN"),List.of("UPSERT"),false);}
    @Override public void refreshCaches() {}
    @Override @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=Exception.class) public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class) public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        if(batch==null || !batch.full())throw new IllegalArgumentException("DIRECT_FULL_SOURCE_REQUIRED");
        var run=context.require(new SyncOwnerRunContext.Request(batch,ADAPTER,apply));var d=run.definition();
        SyncSecureLicenseReader.validate(d,d.sources().getFirst());
        var s=d.sources().getFirst();var expected=DirectLicenseApprovalManifest.load(s).byId();var approved=expected.keySet();
        boolean streaming=DirectSecureLicenseStream.requested(d);
        if(streaming && (batch.rows().isEmpty() || batch.rows().size()>200))fail("SOURCE_INVALID");
        if(approved.isEmpty())fail("EXPLICIT_APPROVAL_REQUIRED");
        var prior=new HashMap<String,Binding>();
        for(var binding:batch.bindings())if(!OBJECT.equals(binding.object()) || prior.put(binding.sourceKey(),binding)!=null)fail("BINDING_INVALID");
        var rows=new LinkedHashMap<String,Row>();var originals=new HashMap<String,Map<String,String>>();var numbers=new LinkedHashSet<String>();
        for(var row:batch.rows()) {
            if(!OBJECT.equals(row.object()) || !row.fields().keySet().equals(new HashSet<>(COLUMNS)) || !approved.contains(row.sourceKey())
                || !Objects.equals(row.sourceKey(),text(row.fields().get("id"))) || rows.put(row.sourceKey(),row)!=null)fail("SOURCE_INVALID");
            var raw=new LinkedHashMap<String,String>();ORIGINAL.forEach(n->raw.put(n,text(row.fields().get(n))));originals.put(row.sourceKey(),raw);
            var actual=new Approved(row.sourceKey(),raw.get("sn"),raw.get("specModel"),raw.get("contract"),raw.get("contractType"),raw.get("item"),raw.get("status"));
            if(!Objects.equals(expected.get(row.sourceKey()),actual))fail("APPROVED_FACTS_CHANGED");
            var old=prior.get(row.sourceKey());
            if(old==null && row.targetId()!=null || old!=null && (!Objects.equals(row.targetId(),old.targetId())
                || !(old.lastFields().get("originalFields") instanceof Map<?,?> fields) || !fields.keySet().equals(raw.keySet())
                || raw.keySet().stream().anyMatch(n->!Objects.equals(raw.get(n),text(fields.get(n))))
                || !Objects.equals(text(old.lastFields().get("codeMac")),text(row.fields().get("codeMac")))))fail("BOUND_SOURCE_CHANGED");
            if(raw.get("contract")!=null && !raw.get("contract").isBlank())numbers.add(raw.get("contract"));
        }
        if(!streaming && !rows.keySet().equals(approved))fail("APPROVED_SOURCE_INCOMPLETE");
        // The controlled reader enforces manifest completeness across chunks; this command owns only its chunk.
        approved=rows.keySet();
        var resolved=new HashMap<String,Long>();
        for(String number:numbers) {
            var matches=records.selectContractIds(new cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectLicenseHistoryMapper.ContractIdentity(run.tenantId(),number));
            if(matches.size()==1)resolved.put(number,matches.getFirst());
        }
        var licenses=rows.values().stream().map(row->{var raw=originals.get(row.sourceKey());
            return new DppmsLicenseRecordApi.License(row.sourceKey(),raw.get("sn"),raw.get("specModel"),raw.get("contract"),raw.get("contractType"),raw.get("item"),raw.get("status"),resolved.get(raw.get("contract")),
                new DppmsLicenseRecordApi.Envelope(row.sourceKey(),text(row.fields().get("ciphertext")),text(row.fields().get("keyId")),text(row.fields().get("codeMac"))));}).toList();
        var command=new DppmsLicenseRecordApi.Command(run.tenantId(),run.actorId(),null,run.run().getId(),
            run.run().getSourceUpper()==null?run.run().getStartedAt():run.run().getSourceUpper(),licenses);
        var preview=new HashMap<String,DirectLicenseHistoryPersistence.Preview>();
        for(var result:owner.preview(command))if(!approved.contains(result.sourceKey()) || preview.put(result.sourceKey(),result)!=null)fail("OWNER_INVALID");
        if(!preview.keySet().equals(approved))fail("OWNER_INCOMPLETE");
        var targets=new HashMap<String,Long>();
        if(apply) {
            for(var result:owner.apply(command))if(!approved.contains(result.sourceKey()) || result.id()==null || result.id()<=0 || targets.put(result.sourceKey(),result.id())!=null)fail("OWNER_INVALID");
        } else {
            for(var result:preview.values())targets.put(result.sourceKey(),result.targetId());
        }
        if(!targets.keySet().equals(approved))fail("OWNER_INCOMPLETE");
        var changes=new ArrayList<Change>();
        for(var row:rows.values()) {
            var old=prior.get(row.sourceKey());Long id=targets.get(row.sourceKey());var checked=preview.get(row.sourceKey());
            if(old!=null&&!Objects.equals(old.targetId(),id) || apply && checked.targetId()!=null&&!Objects.equals(checked.targetId(),id))fail("OWNER_TARGET_CHANGED");
            String json=originals.get(row.sourceKey()).entrySet().stream().map(e->JsonUtils.toJsonString(e.getKey())+":"+(e.getValue()==null?"null":JsonUtils.toJsonString(e.getValue())))
                .collect(java.util.stream.Collectors.joining(",","{","}"));
            var after=new LinkedHashMap<String,Object>();after.put("originalFields",JsonUtils.getObjectMapper().readTree(json));after.put("codeMac",text(row.fields().get("codeMac")));
            after.put("keyId",text(row.fields().get("keyId")));after.put("pendingAssociations",checked.pendingAssociations());
            if(!checked.sourceIssues().isEmpty())after.put("sourceIssues",checked.sourceIssues());
            String message=checked.pendingAssociations().isEmpty()?"已承接批准许可历史":"许可历史承接；关联仍待处理："+String.join(",",checked.pendingAssociations());
            if(!checked.sourceIssues().isEmpty())message+="；来源问题：LICENSE_CODE_EMPTY，原始空字符串已加密保留";
            changes.add(new Change(OBJECT,row.sourceKey(),id,"UNCHANGED".equals(checked.action())?"UNCHANGED":"CREATED",old==null?Map.of():old.lastFields(),after,
                message));
        }
        return List.copyOf(changes);
    }
    static boolean hasSourceIssue(Change change) {
        return OBJECT.equals(change.object()) && change.after()!=null
                && List.of("LICENSE_CODE_EMPTY").equals(change.after().get("sourceIssues"));
    }
    private static String text(Object v){return v==null || v instanceof tools.jackson.databind.JsonNode n&&n.isNull()?null:v.toString();}
    private static void fail(String code){throw new IllegalArgumentException("DPPMS_SECURE_LICENSE_"+code);}
}
