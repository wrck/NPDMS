package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemRoleMigrationApi.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsRoleAuditCorrectionAdapterTest {
    final SystemRoleMigrationApi roles=mock(SystemRoleMigrationApi.class);
    final SyncTaskService tasks=mock(SyncTaskService.class);
    final SyncRunMapper runs=mock(SyncRunMapper.class);
    final SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    final DppmsRoleAuditCorrectionAdapter adapter=new DppmsRoleAuditCorrectionAdapter(roles,tasks,runs,bindings);
    SyncRunDO run,receipt; SyncBindingDO binding; Map<String,Object> fields,expected;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setId(12L).setAdapter(DppmsRoleAuditCorrectionAdapter.ADAPTER)
                .setSourceSystem("DPPMS").setConnectionId(18L).setVersion(0).setActiveRunId(13L));
        when(tasks.required(20L)).thenReturn(new SyncTaskDO().setId(20L).setAdapter("DPPMS_ROLES").setSourceSystem("DPPMS").setConnectionId(18L));
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setPreview(true).setStatus("VALIDATING");run.setTenantId(1L);
        receipt=new SyncRunDO().setId(21L).setTaskId(20L).setPreview(false).setStatus("SUCCESS");receipt.setTenantId(1L);receipt.setCreator("1");
        when(runs.selectScoped(any())).thenAnswer(c -> ((SyncQueries.Id)c.getArgument(0)).id()==13L?run:receipt);
        var history=LocalDateTime.of(2015,3,24,11,22);
        var restored=new Role(77L,new Definition("projectViewer","项目查阅员",106,null,null,history,null,history),0,2,5,Set.of());
        expected=DppmsRoleSyncAdapter.fields(restored);expected.put("sourceCode","projectViewer");expected.put("_sourceUpdatedAt",history.toString());
        String json=withNulls(expected);
        binding=new SyncBindingDO().setTaskId(20L).setSourceKey("6").setTargetId(77L).setLastRunId(21L).setFieldsJson(json);
        when(bindings.selectSourceKeys(any())).thenReturn(List.of(binding));
        receipt.setResultJson("[{\"object\":\"FND_ROLE\",\"sourceKey\":\"6\",\"targetId\":77,\"action\":\"CREATED\",\"before\":{},\"after\":"+json+",\"message\":\"created\"}]");
        fields=new LinkedHashMap<>();DppmsRoleAuditCorrectionAdapter.SOURCE_COLUMNS.forEach(c -> fields.put(c,null));fields.put("id",6);fields.put("roleName","项目查阅员");
        String original=withNulls(fields);
        fields.put(DppmsRoleAuditCorrectionAdapter.PROOF,JsonUtils.toJsonString(new DppmsRoleAuditCorrectionAdapter.Proof(20L,21L,77L,"1",json,original)));
        when(roles.previewAuditCorrection(any())).thenAnswer(c -> {
            AuditCorrectionRequest r=c.getArgument(0);var e=r.entries().getFirst();return List.of(new AuditCorrectionResult(e.expectedCurrent(),e.restored(),"UPDATED"));
        });
        when(roles.applyAuditCorrection(any())).thenAnswer(c -> {
            AuditCorrectionRequest r=c.getArgument(0);var e=r.entries().getFirst();return List.of(new AuditCorrectionResult(e.expectedCurrent(),e.restored(),"UPDATED"));
        });
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    private static String withNulls(Map<String,Object> fields) {
        return "{"+fields.entrySet().stream().map(e -> JsonUtils.toJsonString(e.getKey())+":"
                +(e.getValue()==null?"null":JsonUtils.toJsonString(e.getValue())))
                .collect(java.util.stream.Collectors.joining(","))+"}";
    }
    Batch batch(){return new Batch("integration:1:12",List.of(new Row("fnd_roles","6",fields,null)),List.of(),true,"RETAIN",true,"UPSERT",false);}
    @Test void exactCreatedReceiptAndBindingProduceOnlyNullActorRestoration() {
        var change=adapter.preview(batch()).getFirst();assertEquals(77L,change.targetId());
        assertEquals("1",change.before().get("creator"));assertNull(change.after().get("creator"));
        assertEquals(change.before().get("updateTime"),change.after().get("updateTime"));
        verify(roles,never()).applyAuditCorrection(any());run.setPreview(false);run.setStatus("APPLYING");
        assertEquals("UPDATED",adapter.apply(batch()).getFirst().action());
    }
    @Test void sourceOrBindingDriftStopsBeforeOwnerWrite() {
        fields.put("defaultPage","changed");assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));fields.put("defaultPage",null);
        binding.setLastRunId(22L);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));verifyNoInteractions(roles);
    }
    @Test void existingAdoptedRoleOrWrongActualReceiptCannotBeCorrected() {
        receipt.setResultJson(JsonUtils.toJsonString(List.of(new Change("FND_ROLE","6",77L,"ADOPTED",expected,expected,"adopted"))));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));verifyNoInteractions(roles);
    }
    @Test void runStageAndOriginalConnectionMustMatch() {
        run.setPreview(false);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));run.setPreview(true);
        when(tasks.required(20L)).thenReturn(new SyncTaskDO().setId(20L).setAdapter("DPPMS_ROLES").setSourceSystem("DPPMS").setConnectionId(99L));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));verifyNoInteractions(roles);
    }
}
