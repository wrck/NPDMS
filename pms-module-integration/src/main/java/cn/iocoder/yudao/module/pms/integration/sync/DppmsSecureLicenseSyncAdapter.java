package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsLicenseRecordApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsCommerceEntityResolveApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSecureLicenseSyncTemplate.*;
/** Uses the existing formal license and contract Owners, including their authorization and immutable replay rules. */
@Component @RequiredArgsConstructor
public class DppmsSecureLicenseSyncAdapter implements DataSyncAdapter {
    private final SyncOwnerRunContext context;
    private final DppmsLicenseRecordApi owner;
    private final DppmsCommerceEntityResolveApi contracts;
    @Override public boolean requiresAllBindings(){return false;}
    @Override public Descriptor descriptor(){return new Descriptor(ADAPTER,"DPPMS受控许可历史",List.of(new ObjectDescriptor(OBJECT,"批准许可历史",
        COLUMNS.stream().map(n->new Field(n,n,"STRING",false)).toList(),"AST","DeviceLicenseRecord","ast_device_license_record",false)),List.of("RETAIN"),List.of("UPSERT"),false);}
    @Override public void refreshCaches() {}
    @Override @Transactional(rollbackFor=Exception.class) public List<Change> preview(Batch batch){return execute(batch,false);}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class) public List<Change> apply(Batch batch){return execute(batch,true);}
    private List<Change> execute(Batch batch,boolean apply) {
        var run=context.require(new SyncOwnerRunContext.Request(batch,ADAPTER,apply));var d=run.definition();
        SyncSecureLicenseReader.validate(d,d.sources().getFirst());
        var s=d.sources().getFirst();var expected=new HashMap<String,Approved>();approved(s).forEach(a->expected.put(a.id(),a));var approved=expected.keySet();
        Long projectId=SyncFieldMapper.primaryKey(Objects.toString(s.parameters().get("ownerProjectId"),""));
        if(approved.isEmpty() || projectId<=0)fail("EXPLICIT_APPROVAL_REQUIRED");
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
        if(!rows.keySet().equals(approved))fail("APPROVED_SOURCE_INCOMPLETE");
        var resolved=new HashMap<String,Long>();
        for(var result:contracts.resolveLicenseContracts(new DppmsCommerceEntityResolveApi.LicenseContracts(run.tenantId(),run.actorId(),projectId,List.copyOf(numbers)))) {
            if(!numbers.contains(result.contractNo()) || resolved.containsKey(result.contractNo()))fail("CONTRACT_OWNER_INVALID");resolved.put(result.contractNo(),result.contractId());
        }
        if(!resolved.keySet().equals(numbers))fail("CONTRACT_OWNER_INCOMPLETE");
        var licenses=rows.values().stream().map(row->{var raw=originals.get(row.sourceKey());
            return new DppmsLicenseRecordApi.License(row.sourceKey(),raw.get("sn"),raw.get("specModel"),raw.get("contract"),raw.get("contractType"),raw.get("item"),raw.get("status"),resolved.get(raw.get("contract")),
                new DppmsLicenseRecordApi.Envelope(row.sourceKey(),text(row.fields().get("ciphertext")),text(row.fields().get("keyId")),text(row.fields().get("codeMac"))));}).toList();
        var command=new DppmsLicenseRecordApi.Command(run.tenantId(),run.actorId(),projectId,run.run().getId(),
            run.run().getSourceUpper()==null?run.run().getStartedAt():run.run().getSourceUpper(),licenses);
        var preview=new HashMap<String,DppmsLicenseRecordApi.Preview>();
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
            changes.add(new Change(OBJECT,row.sourceKey(),id,"UNCHANGED".equals(checked.action())?"UNCHANGED":"CREATED",old==null?Map.of():old.lastFields(),after,
                checked.pendingAssociations().isEmpty()?"已承接批准许可历史":"许可历史承接；关联仍待处理："+String.join(",",checked.pendingAssociations())));
        }
        return List.copyOf(changes);
    }
    private static String text(Object v){return v==null || v instanceof tools.jackson.databind.JsonNode n&&n.isNull()?null:v.toString();}
    private static void fail(String code){throw new IllegalArgumentException("DPPMS_SECURE_LICENSE_"+code);}
}
