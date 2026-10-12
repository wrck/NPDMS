package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceSourceRelationshipApi;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsDeviceSourceRelationshipApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDeviceRelationshipSyncTemplate.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsDeviceRelationshipAuditCorrectionAdapter.*;

class DppmsDeviceRelationshipAuditCorrectionAdapterTest {
    final DppmsDeviceSourceRelationshipApi owner=mock(DppmsDeviceSourceRelationshipApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final DeviceRelationshipAuditEvidenceMapper evidence=mock(DeviceRelationshipAuditEvidenceMapper.class);
    final DppmsDeviceRelationshipAuditCorrectionAdapter adapter=new DppmsDeviceRelationshipAuditCorrectionAdapter(owner,tasks,evidence);
    SyncTaskDO task,originalTask;SyncRunDO run;SyncBindingDO binding;
    final LocalDateTime date=LocalDateTime.of(2023,1,2,3,4,5,123000000);
    final DeviceRelationshipAuditEvidenceMapper.Receipt receipt=new DeviceRelationshipAuditEvidenceMapper.Receipt(
            9L,1L,8L,"SUCCESS",false,false,100,"a".repeat(64),"b".repeat(64),"{\"CREATED\":100}",date,date);
    Map<String,Object> source;AuditRecord before,after;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setVersion(0).setActiveRunId(13L).setConnectionId(18L).setAdapter(KEY)
                .setSourceSystem("DPPMS").setDefinition(JsonUtils.toJsonString(DppmsDeviceRelationshipAuditCorrectionTemplate.create(18L)));task.setTenantId(1L);
        originalTask=new SyncTaskDO().setId(8L).setConnectionId(18L).setAdapter("TABLE_MAPPING:device-sn").setSourceSystem("DPPMS");originalTask.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setPreview(false).setStatus("APPLYING");run.setTenantId(1L);run.setCreator("7");
        when(tasks.locked(8L)).thenReturn(originalTask);
        when(evidence.selectCorrectionContextForUpdate(any())).thenAnswer(c->new DeviceRelationshipAuditEvidenceMapper.CorrectionContext(
                task.getId(),task.getTenantId(),task.getAdapter(),task.getSourceSystem(),task.getConnectionId(),task.getVersion(),
                "ONCE",false,task.getActiveRunId(),run.getTaskId(),run.getTenantId(),run.getConfigVersion(),run.getPreview(),run.getStatus(),run.getCreator()));
        when(evidence.selectReceiptForUpdate(any())).thenReturn(receipt);
        source=new LinkedHashMap<>();ORIGINAL_COLUMNS.forEach(n->source.put(n,null));
        source.put("id",20);source.put("sn1","MAIN");source.put("sn2","EXTRA");source.put("item1","");source.put("contract","C1");
        source.put("createtime","2023-01-02 03:04:05.1230000");
        binding=new SyncBindingDO().setId(90L).setTaskId(8L).setObjectKey("DEVICE_RELATION").setSourceObject(OBJECT).setSourceKey("20")
                .setTargetId(40L).setLastRunId(9L).setTargetShared(true).setFieldsJson(JsonUtils.toJsonString(Map.of("_syncSourcePayload",source)));
        binding.setTenantId(1L);binding.setCreator("data_sync");binding.setUpdater("data_sync");binding.setCreateTime(date);binding.setUpdateTime(date);binding.setDeleted(false);
        when(evidence.selectBindingsForUpdate(any())).thenAnswer(c->c.getArgument(0,SyncQueries.SourceKeys.class).sourceKeys().isEmpty()?List.of():List.of(binding));
        before=new AuditRecord(40L,1L,"MAIN","EXTRA","",null,"EXTRA_SN","C1",date,null,"DPPMS","20",null,0,"data_sync",date.plusYears(3),"data_sync",date.plusYears(3),false);
        after=new AuditRecord(40L,1L,"MAIN","EXTRA","",null,"EXTRA_SN","C1",date,null,"DPPMS","20",null,1,"data_sync",date,"data_sync",date,false);
        when(owner.applyAuditCorrection(any())).thenReturn(List.of(new AuditResult("20","UPDATED",before,after)));
        when(owner.previewAuditCorrection(any())).thenReturn(List.of(new AuditResult("20","UPDATED",before,after)));
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Row row(){
        var f=new LinkedHashMap<>(source);f.put(PROOF,new Proof(40L,90L,before.fingerprint(),bindingFingerprint(binding),sourceFingerprint(source)));
        f.put(RECEIPT,new RunProof(8L,receipt));return new Row(OBJECT,"20",f,null);
    }
    Batch batch(Row r,List<Binding> b){return new Batch("integration:1:12",List.of(r),b,false,"RETAIN",false,"UPSERT",false);}
    @Test void correctionUsesOriginalReceiptBindingAndActorWithinStreamingTransaction(){
        var result=adapter.apply(batch(row(),List.of())).getFirst();assertEquals("UPDATED",result.action());
        verify(owner).applyAuditCorrection(argThat(r->r.actorId().equals(7L) && r.entries().getFirst().expectedBeforeFingerprint().equals(before.fingerprint())));
        assertEquals(19,((Map<?,?>)result.after().get("relationship")).size());
        assertEquals(8,((Map<?,?>)result.after().get("originalFields")).size());
        assertTrue(adapter.supportsStreaming());assertTrue(adapter.sharesTargetAcrossSources());
    }
    @Test void changedRawSourceOrOldBindingNeverReachesBusinessOwner(){
        var first=row();first.fields().put("item1","CHANGED");assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(first,List.of())));
        var reviewed=row();binding.setUpdater("manual");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(reviewed,List.of())));verifyNoInteractions(owner);
    }
    @Test void changedReceiptOrRunningOriginalTaskBlocksWholeChunk(){
        var row=row();when(evidence.selectReceiptForUpdate(any())).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of())));
        when(evidence.selectReceiptForUpdate(any())).thenReturn(receipt);originalTask.setActiveRunId(99L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of())));verifyNoInteractions(owner);
    }
    @Test void staleVersionForeignTenantOrInvalidRunPhaseCannotReachOwner(){
        run.setConfigVersion(1);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of())));
        run.setConfigVersion(0);run.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of())));
        run.setTenantId(1L);run.setStatus("SUCCESS");assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of())));
        verifyNoInteractions(owner);verify(tasks,never()).required(anyLong());
    }
    @Test void onlyReviewedSelfRowsAreSkippedAndAllEightOriginalFieldsRemain(){
        var f=new LinkedHashMap<>(source);f.put("sn2","MAIN");f.put("item2","");
        f.put(PROOF,new Proof(null,null,null,null,sourceFingerprint(f)));f.put(RECEIPT,new RunProof(8L,receipt));
        var row=new Row(OBJECT,"20",f,null);var result=adapter.apply(batch(row,List.of())).getFirst();
        assertEquals("SKIPPED",result.action());assertNull(result.targetId());assertEquals(8,((Map<?,?>)result.after().get("originalFields")).size());
        f.put(PROOF,null);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of())));
        verifyNoInteractions(owner);
    }
    @Test void replayUsesLastConfirmedNineteenFieldsAndOriginalProofRemainsProtected(){
        var raw=new LinkedHashMap<String,Object>(source);raw.replaceAll((k,v)->v==null?tools.jackson.databind.node.NullNode.getInstance():v.toString());
        var prior=new Binding(OBJECT,"20",40L,Map.of("relationship",JsonUtils.parseMap(JsonUtils.toJsonString(after)),"originalFields",raw));
        var row=row();row=new Row(row.object(),row.sourceKey(),row.fields(),40L);
        when(owner.applyAuditCorrection(any())).thenReturn(List.of(new AuditResult("20","UNCHANGED",after,after)));
        assertEquals("UNCHANGED",adapter.apply(batch(row,List.of(prior))).getFirst().action());
        verify(owner).applyAuditCorrection(argThat(r->r.entries().getFirst().expectedBeforeFingerprint().equals(after.fingerprint())));
    }
    @Test void previewDoesNotCallMutationAndTemplateReadsSelfRows(){
        run.setPreview(true).setStatus("VALIDATING");adapter.preview(batch(row(),List.of()));verify(owner,never()).applyAuditCorrection(any());
        var definition=DppmsDeviceRelationshipAuditCorrectionTemplate.create(18L);
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));assertTrue(definition.sources().getFirst().filters().isEmpty());
    }
}
