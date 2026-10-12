package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import static cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsUserAuditCorrectionAdapterTest {
    final SystemUserMigrationApi users=mock(SystemUserMigrationApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    final DppmsUserAuditCorrectionAdapter adapter=new DppmsUserAuditCorrectionAdapter(users,tasks,runs,bindings);
    SyncRunDO run,receipt;SyncBindingDO binding;Map<String,Object> fields;User restored;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setId(12L).setAdapter(DppmsUserAuditCorrectionAdapter.ADAPTER).setSourceSystem("DPPMS").setConnectionId(18L).setVersion(0).setActiveRunId(13L));
        when(tasks.required(20L)).thenReturn(new SyncTaskDO().setId(20L).setAdapter("DPPMS_USERS").setSourceSystem("DPPMS").setConnectionId(18L));
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setPreview(true).setStatus("VALIDATING");run.setTenantId(1L);run.setCreator("1");
        receipt=new SyncRunDO().setId(21L).setTaskId(20L).setPreview(false).setStatus("SUCCESS");receipt.setTenantId(1L);receipt.setCreator("1");
        when(runs.selectScoped(any())).thenAnswer(call->((SyncQueries.Id)call.getArgument(0)).id()==13L?run:receipt);
        var history=LocalDateTime.of(2015,3,24,11,22);
        restored=new User(77L,new Profile("c00001","原姓名",null,null,Set.of(),null,null,1,null,1,"00001",JsonUtils.parseTree("{\"optional\":null}")),new Audit(null,history,null,history));
        var json=JsonUtils.toJsonString(DppmsUserSyncAdapter.fields(restored));
        binding=new SyncBindingDO().setTaskId(20L).setSourceKey("23").setTargetId(77L).setLastRunId(21L).setFieldsJson(json);
        when(bindings.selectSourceKeys(any())).thenReturn(List.of(binding));
        fields=new LinkedHashMap<>();DppmsUserSyncTemplate.COLUMNS.get("fnd_user_info").forEach(c->fields.put(c,null));fields.put("id",23);fields.put("username","c00001");
        var original="{"+fields.entrySet().stream().map(e->"\""+e.getKey()+"\":"+(e.getValue()==null?"null":JsonUtils.toJsonString(e.getValue()))).collect(Collectors.joining(","))+"}";
        fields.put(DppmsUserAuditCorrectionAdapter.PROOF,JsonUtils.toJsonString(new DppmsUserAuditCorrectionAdapter.Proof(20L,21L,77L,"1",json,original)));
        when(users.previewAuditCorrection(any())).thenAnswer(call->{AuditCorrectionRequest request=call.getArgument(0);var e=request.entries().getFirst();return List.of(new AuditCorrectionResult(e.expectedCurrent(),e.restored(),"UPDATED"));});
        when(users.applyAuditCorrection(any())).thenAnswer(call->{AuditCorrectionRequest request=call.getArgument(0);var e=request.entries().getFirst();return List.of(new AuditCorrectionResult(e.expectedCurrent(),e.restored(),"UPDATED"));});
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    Batch batch(){return new Batch("integration:1:12",List.of(new Row("fnd_user_info","23",fields,null)),List.of(),true,"RETAIN",true,"UPSERT",false);}
    @Test void exactOriginalRunBindingAndRawFieldsProduceOnlyTheReviewedNullUpdaterCorrection() {
        var changes=adapter.preview(batch());assertEquals(1,changes.size());assertEquals(77L,changes.getFirst().targetId());
        assertEquals("1",((Audit)changes.getFirst().before().get("audit")).updater());assertNull(((Audit)changes.getFirst().after().get("audit")).updater());
        verify(users,never()).apply(any());run.setPreview(false);run.setStatus("APPLYING");assertEquals("UPDATED",adapter.apply(batch()).getFirst().action());
    }
    @Test void changedOriginalBindingOrSourceBlocksBeforeAnyBusinessWrite() {
        binding.setLastRunId(22L);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));binding.setLastRunId(21L);
        fields.put("realName","变化");assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));verifyNoInteractions(users);
    }
    @Test void wrongRunIdentityOrNonNullOriginalUpdaterCannotTriggerCorrection() {
        run.setPreview(false);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));run.setPreview(true);
        fields.put("updateBy",81);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));verifyNoInteractions(users);
    }
    @Test void originalReceiptMustBeSuccessfulActualMigrationOnTheSameConnection() {
        receipt.setPreview(true);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));receipt.setPreview(false);
        when(tasks.required(20L)).thenReturn(new SyncTaskDO().setId(20L).setAdapter("DPPMS_USERS").setSourceSystem("DPPMS").setConnectionId(99L));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));verifyNoInteractions(users);
    }
    @Test void generatedTimeRequiresPreviousSuccessfulCorrectionAndExactTargetEvidence() {
        var previous=new SyncRunDO().setId(41L).setTaskId(30L).setPreview(false).setStatus("SUCCESS")
                .setStartedAt(LocalDateTime.of(2026,10,9,21,9,30)).setFinishedAt(LocalDateTime.of(2026,10,9,21,9,33));previous.setTenantId(1L);
        when(tasks.required(30L)).thenReturn(new SyncTaskDO().setId(30L).setAdapter(DppmsUserAuditCorrectionAdapter.ADAPTER).setConnectionId(18L));
        doAnswer(call->{Long id=((SyncQueries.Id)call.getArgument(0)).id();return id==13L?run:id==21L?receipt:previous;}).when(runs).selectScoped(any());
        var corrected=new SyncBindingDO().setTaskId(30L).setTargetId(77L).setSourceKey("23").setLastRunId(41L).setFieldsJson(binding.getFieldsJson());
        when(bindings.selectSourceKeys(any())).thenAnswer(call->((SyncQueries.SourceKeys)call.getArgument(0)).taskId()==20L?List.of(binding):List.of(corrected));
        var original=JsonUtils.parseObject(fields.get(DppmsUserAuditCorrectionAdapter.PROOF).toString(),DppmsUserAuditCorrectionAdapter.Proof.class);
        var current=new User(restored.id(),restored.profile(),new Audit(restored.audit().creator(),restored.audit().createTime(),null,previous.getStartedAt().plusSeconds(1)));
        fields.put(DppmsUserAuditCorrectionAdapter.PROOF,JsonUtils.toJsonString(new DppmsUserAuditCorrectionAdapter.Proof(20L,21L,77L,"1",original.expectedFieldsJson(),original.expectedSourceFieldsJson(),41L,
                JsonUtils.toJsonString(DppmsUserSyncAdapter.fields(current)))));
        adapter.preview(batch());verify(users).previewAuditCorrection(argThat(r->r.generatedUpdateFrom().equals(previous.getStartedAt()) && r.generatedUpdateTo().equals(previous.getFinishedAt())));
        previous.setStatus("FAILED");assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));
    }
}
