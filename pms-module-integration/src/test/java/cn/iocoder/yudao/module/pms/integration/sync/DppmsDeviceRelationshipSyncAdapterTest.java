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

class DppmsDeviceRelationshipSyncAdapterTest {
    final DppmsDeviceSourceRelationshipApi owner=mock(DppmsDeviceSourceRelationshipApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final DppmsDeviceRelationshipSyncAdapter adapter=new DppmsDeviceRelationshipSyncAdapter(owner,tasks,runs);
    SyncTaskDO task;SyncRunDO run;
    @BeforeEach void setup(){
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setVersion(1).setActiveRunId(13L).setAdapter(ADAPTER)
                .setSourceSystem("DPPMS").setDefinition(JsonUtils.toJsonString(create(18L)));task.setTenantId(1L);
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(1).setStatus("APPLYING").setPreview(false);
        run.setTenantId(1L);run.setCreator("7");
        when(tasks.required(12L)).thenReturn(task);when(runs.selectScoped(any())).thenReturn(run);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Row row(){
        var fields=new LinkedHashMap<String,Object>();ORIGINAL_COLUMNS.forEach(name->fields.put(name,null));
        fields.put("id",20);fields.put("sn1","MAIN");fields.put("sn2","EXTRA");fields.put("item1","");
        fields.put("contract","C1");fields.put("createtime","2023-01-02 03:04:05.1230000");
        return new Row(OBJECT,"20",fields,null);
    }
    Batch batch(Row row,List<Binding> bindings,boolean adopt){
        return new Batch("integration:1:12",List.of(row),bindings,false,"RETAIN",adopt,"UPSERT",false);
    }
    RelationshipRecord record(Long id){
        var time=LocalDateTime.parse("2023-01-02T03:04:05.123");
        return new RelationshipRecord(id,"MAIN","EXTRA","",null,"EXTRA_SN","C1",time,null,
                "DPPMS","20",null,0,"7",time,"7",time);
    }
    void prepareCreate(){
        when(owner.previewRelationships(any())).thenReturn(List.of(new ImportResult("20","CREATE",null,record(null))));
        when(owner.applyRelationships(any())).thenReturn(List.of(new ImportResult("20","CREATE",null,record(40L))));
    }
    @Test void templateIncludesEveryOriginalColumnAndDoesNotFilterSelfRelations(){
        var definition=create(18L);
        assertDoesNotThrow(()->new SyncDefinitionValidator(List.of(adapter)).validate(definition));
        var source=definition.sources().getFirst();assertEquals(ORIGINAL_COLUMNS,source.columns());
        assertTrue(source.filters().isEmpty());assertEquals("SQL",source.readMode());
        assertTrue(MysqlSyncReader.compile(source).sql().contains("fb_shipment_barcode_relation"));
        assertTrue(adapter.supportsStreaming());assertFalse(adapter.requiresAllBindings());
    }
    @Test void actorComesFromControlledRunAndRawNullsRemainExplicitInBindingEvidence(){
        prepareCreate();
        var result=adapter.apply(batch(row(),List.of(),false)).getFirst();
        verify(owner).applyRelationships(argThat(command->command.actorId().equals(7L)
                && command.tenantId().equals(1L) && command.relations().getFirst().sourceUpdateTime()==null
                && command.relations().getFirst().item1().isEmpty()));
        assertEquals(40L,result.targetId());
        var raw=(Map<?,?>)result.after().get("originalFields");assertEquals(8,raw.size());assertTrue(raw.containsKey("updatetime"));
    }
    @Test void previewNeverCallsMutation(){
        prepareCreate();run.setPreview(true).setStatus("VALIDATING");
        assertNull(adapter.preview(batch(row(),List.of(),false)).getFirst().targetId());
        verify(owner,never()).applyRelationships(any());
    }
    @Test void replayChecksFullBusinessRecordAndDecodedJsonNulls(){
        prepareCreate();var first=adapter.apply(batch(row(),List.of(),false)).getFirst();
        var last=JsonUtils.parseMap(JsonUtils.toJsonString(first.after()));
        var source=row();var boundRow=new Row(source.object(),source.sourceKey(),source.fields(),40L);
        var existing=record(40L);
        when(owner.previewRelationships(any())).thenReturn(List.of(new ImportResult("20","UNCHANGED",existing,existing)));
        when(owner.applyRelationships(any())).thenReturn(List.of(new ImportResult("20","UNCHANGED",existing,existing)));
        assertEquals("UNCHANGED",adapter.apply(batch(boundRow,List.of(new Binding(OBJECT,"20",40L,last)),false)).getFirst().action());
        ((Map<String,Object>)last.get("relationship")).put("updater","manual-change");
        clearInvocations(owner);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(boundRow,List.of(new Binding(OBJECT,"20",40L,last)),false)));
        verify(owner,never()).applyRelationships(any());
    }
    @Test void existingAdoptionMustBeExplicitAndCannotAbsorbAuditConflict(){
        var existing=record(40L);
        when(owner.previewRelationships(any())).thenReturn(List.of(new ImportResult("20","UNCHANGED",existing,existing)));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        verify(owner,never()).applyRelationships(any());
        when(owner.previewRelationships(any())).thenThrow(new IllegalArgumentException("EXISTING_AUDIT_REQUIRES_CORRECTION"));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),true)));
        verify(owner,never()).applyRelationships(any());
    }
    @Test void staleRunCrossTenantUnknownActorAndMissingRawColumnsCannotReachOwner(){
        run.setConfigVersion(2);assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        run.setConfigVersion(1);run.setCreator("scheduled_sync");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        run.setCreator("7");TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        TenantContextHolder.setTenantId(1L);var row=row();row.fields().remove("updatetime");
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row,List.of(),false)));
        verifyNoInteractions(owner);
    }
    @Test void wrongOwnerReceiptCannotBeAccepted(){
        when(owner.previewRelationships(any())).thenReturn(List.of(new ImportResult("21","CREATE",null,record(null))));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch(row(),List.of(),false)));
        verify(owner,never()).applyRelationships(any());
    }
}
