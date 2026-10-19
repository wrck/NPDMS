package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.DppmsContractCompletionApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsDeliveryCompletionServiceTest {
    SyncTaskMapper tasks;SyncRunMapper runs;SyncBindingMapper bindings;DppmsContractCompletionApi owner;
    DppmsDeliveryCompletionService service;SyncDefinition definition;SyncRunDO current,snapshot;SyncTaskDO receivables;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        tasks=mock(SyncTaskMapper.class);runs=mock(SyncRunMapper.class);bindings=mock(SyncBindingMapper.class);owner=mock(DppmsContractCompletionApi.class);
        service=new DppmsDeliveryCompletionService(tasks,runs,bindings,owner);
        definition=SyncDefinition.builder().adapter("TABLE_MAPPING").sourceSystem("DPPMS").connectionId(7L).taskKey("delivery-full-sync")
                .sources(List.of(SyncDefinition.Source.builder().readMode("LOCAL_OWNER").build())).build();
        current=new SyncRunDO().setId(150L).setTaskId(15L).setPreview(false).setFullSnapshot(true).setSummaryJson("{\"CREATED\":3}");
        current.setTenantId(1L);current.setCreator("2");
        receivables=new SyncTaskDO().setId(24L).setConnectionId(7L).setAdapter(DppmsContractReceivableSyncTemplate.ADAPTER).setSourceSystem("DPPMS").setVersion(4);
        snapshot=new SyncRunDO().setId(240L).setStatus("SUCCESS").setFullSnapshot(true).setConfigVersion(4)
                .setConfigSnapshot(JsonUtils.toJsonString(DppmsContractReceivableSyncTemplate.create(7L))).setSummaryJson("{\"CREATED\":1,\"SKIPPED\":2}");
        when(tasks.selectSourceAdapter(any())).thenReturn(List.of(receivables));when(tasks.selectForUpdate(any())).thenReturn(receivables);
        when(runs.selectLatestFormalRoot(any())).thenReturn(snapshot);
        when(owner.complete(any())).thenReturn(new DppmsContractCompletionApi.Result(1));
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void reusesQualifiedExistingP24AndOnlyCurrentRunContractIds() {
        when(bindings.selectDeliveryInputs(any())).thenAnswer(call->{
            SyncQueries.DeliveryBindings query=call.getArgument(0);
            assertEquals(1L,query.tenantId());
            if("CONTRACT".equals(query.object())) {
                assertEquals(15L,query.taskId());assertEquals(150L,query.runId());assertEquals("com_sales_order",query.sourceObject());
                return List.of(new SyncBindingDO().setTargetId(10L));
            }
            if("PACKAGE".equals(query.object())) {
                assertEquals(150L,query.runId());assertEquals("fb_shipment",query.sourceObject());
                return List.of(new SyncBindingDO().setTargetId(40L));
            }
            if(query.taskId()==24L)assertNull(query.runId());
            else assertTrue(query.runId()==null || query.runId()==150L);
            return List.of(new SyncBindingDO().setTargetId(query.taskId()==24L?30L:20L));
        });
        assertEquals(1,service.complete(new DppmsDeliveryCompletionService.Completion(current,definition)).changedContracts());
        verify(owner).complete(new DppmsContractCompletionApi.Command(1L,2L,false,new DppmsContractCompletionApi.DeliveryScope(Set.of(10L),Set.of(20L),Set.of(40L)),Set.of(20L),Set.of(30L)));
        verify(runs).selectLatestFormalRoot(new SyncQueries.Task(1L,24L));
    }
    @Test void failedPartialActiveOrWrongConnectionSnapshotsNeverReachOwner() {
        current.setSummaryJson("{\"ISSUE\":1}");assertBlocked();current.setSummaryJson("{}");
        snapshot.setStatus("FAILED");assertBlocked();snapshot.setStatus("SUCCESS");
        snapshot.setFullSnapshot(false);assertBlocked();snapshot.setFullSnapshot(true);
        snapshot.setConfigVersion(3);assertBlocked();snapshot.setConfigVersion(4);
        receivables.setActiveRunId(241L);assertBlocked();receivables.setActiveRunId(null);
        snapshot.setConfigSnapshot(JsonUtils.toJsonString(DppmsContractReceivableSyncTemplate.create(8L)));assertBlocked();
        verifyNoInteractions(owner,bindings);
    }
    @Test void restrictedP24SourceAndMissingCurrentOwnerBindingFailClosed() {
        var restricted=DppmsContractReceivableSyncTemplate.create(7L);
        var source=restricted.sources().getFirst().toBuilder().parameters(Map.of("contractNos","C1")).build();
        snapshot.setConfigSnapshot(JsonUtils.toJsonString(restricted.toBuilder().sources(List.of(source)).build()));assertBlocked();
        snapshot.setConfigSnapshot(JsonUtils.toJsonString(restricted));
        when(bindings.selectDeliveryInputs(any())).thenReturn(List.of(new SyncBindingDO()));assertBlocked();
        verifyNoInteractions(owner);
    }
    @Test void previewValidatesInputsAndNeverClaimsNewVirtualIdsAreFormalIds() {
        current.setPreview(true);
        when(bindings.selectDeliveryInputs(any())).thenReturn(List.of());
        service.complete(new DppmsDeliveryCompletionService.Completion(current,definition));
        verify(owner).complete(new DppmsContractCompletionApi.Command(1L,2L,true,new DppmsContractCompletionApi.DeliveryScope(Set.of(),Set.of(),Set.of()),Set.of(),Set.of()));
    }
    @Test void latestProjectionRefreshKeepsOriginalSourceAndOnlyTouchesThisRunBindings() {
        var raw=Map.<String,Object>of("company_code","A","contract_no","C1");
        var binding=new SyncBindingDO().setId(100L).setTargetId(10L).setFieldsJson(SyncBindingSnapshots.encode(
                new LinkedHashMap<>(Map.of("company_code","A","contract_no","C1","_syncSourcePayload",raw)),true));
        when(bindings.selectDeliveryInputs(any())).thenAnswer(call->{
            SyncQueries.DeliveryBindings query=call.getArgument(0);
            return "CONTRACT".equals(query.object())?List.of(binding):List.of();
        });
        when(bindings.selectDeliveryProjectionPage(any())).thenAnswer(call->{
            SyncQueries.DeliveryProjectionBindings query=call.getArgument(0);
            assertEquals(1L,query.tenantId());assertEquals(15L,query.taskId());assertEquals(150L,query.runId());
            return "CONTRACT".equals(query.object()) && query.afterId()==null?List.of(binding):List.of();
        });
        var projection=new DppmsContractCompletionApi.Projection();projection.setId(10L);projection.setCompanyId(7L);projection.setProjectName("Owner result");
        when(owner.readProjection(any())).thenReturn(List.of(projection));when(bindings.updateById(any(SyncBindingDO.class))).thenReturn(1);
        service.complete(new DppmsDeliveryCompletionService.Completion(current,definition));
        var fields=SyncBindingSnapshots.decode(binding.getFieldsJson());assertEquals(raw,fields.get("_syncSourcePayload"));
        var post=(Map<?,?>)fields.get("_postCompletion");assertEquals("Owner result",post.get("projectName"));assertEquals(7,((Number)post.get("companyId")).intValue());
        verify(bindings,times(1)).updateById(any(SyncBindingDO.class));
    }
    private void assertBlocked() {
        assertThrows(IllegalArgumentException.class,()->service.complete(new DppmsDeliveryCompletionService.Completion(current,definition)));
    }
}
