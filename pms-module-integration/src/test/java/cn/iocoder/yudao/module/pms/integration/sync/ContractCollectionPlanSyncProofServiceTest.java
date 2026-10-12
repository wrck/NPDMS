package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi.*;
import cn.iocoder.yudao.module.pms.integration.api.sync.ContractCollectionPlanSyncProofApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsContractCollectionPlanSyncTemplate.*;

class ContractCollectionPlanSyncProofServiceTest {
    SyncTaskService tasks=mock(SyncTaskService.class);
    SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    ContractCollectionPlanMigrationApi plans=mock(ContractCollectionPlanMigrationApi.class);
    ContractCollectionPlanSyncProofService service=new ContractCollectionPlanSyncProofService(tasks,bindings,plans);
    SyncTaskDO task;
    SyncBindingDO binding;
    ContractCollectionPlanSyncProofApi.Query query=new ContractCollectionPlanSyncProofApi.Query(1L,7L,12L,List.of("10"));

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        task=new SyncTaskDO().setId(12L).setSourceSystem("DPPMS").setAdapter(ADAPTER);task.setTenantId(1L);
        when(tasks.required(12L)).thenReturn(task);
        Map<String,Object> raw=new LinkedHashMap<>();ORIGINAL_COLUMNS.forEach(name->raw.put(name,tools.jackson.databind.node.NullNode.getInstance()));
        raw.put("id","10");raw.put("contractNo"," C1 ");raw.put("afterDaysNum","30");
        Map<String,Object> formal=new LinkedHashMap<>();
        formal.put("id",40);formal.put("tenantId",1);formal.put("contractNo"," C1 ");formal.put("afterDays",30);
        formal.put("createTime","2018-01-02T03:04:05.123");formal.put("updateTime","2019-01-02T03:04:05.456");formal.put("deleted",false);
        binding=new SyncBindingDO().setTaskId(12L).setSourceObject(OBJECT).setObjectKey(OBJECT).setSourceKey("10").setTargetId(40L)
                .setFieldsJson(JsonUtils.toJsonString(Map.of("originalFields",raw,"plan",formal)));binding.setTenantId(1L);
        when(bindings.selectSourceKeys(any())).thenReturn(List.of(binding));
        when(plans.preview(any())).thenAnswer(call->{Request request=call.getArgument(0);var entry=request.entries().getFirst();
            assertEquals("integration:1:12",request.owner());assertEquals(7L,request.actorId());assertFalse(request.adoptExisting());
            assertEquals(LocalDateTime.parse("2018-01-02T03:04:05.123"),entry.lastApplied().createTime());
            return List.of(new Result("10","UNCHANGED",entry.lastApplied(),entry.lastApplied(),"verified"));});
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    @Test void exactTaskBindingAndCurrentOwnerValueAreBothRequired() {
        var proof=service.verify(query).getFirst();assertEquals(40L,proof.targetId());assertEquals(16,proof.originalFields().size());
        assertTrue(proof.originalFields().containsKey("marketingFeedback"));assertNull(proof.originalFields().get("marketingFeedback"));
        verify(bindings).selectSourceKeys(argThat(q->q.tenantId()==1L&&q.taskId()==12L&&OBJECT.equals(q.object())&&OBJECT.equals(q.sourceObject())));
        verify(plans,never()).apply(any());
    }
    @Test void missingOrForeignTenantTaskCannotResolveAnyTarget() {
        assertThrows(IllegalArgumentException.class,()->service.verify(new ContractCollectionPlanSyncProofApi.Query(2L,7L,12L,List.of("10"))));
        task.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->service.verify(query));
        verifyNoInteractions(bindings,plans);
    }
    @Test void incompleteOrAmbiguousBindingCannotClaimCarry() {
        when(bindings.selectSourceKeys(any())).thenReturn(List.of());
        assertThrows(IllegalStateException.class,()->service.verify(query));
        when(bindings.selectSourceKeys(any())).thenReturn(List.of(binding,binding));
        assertThrows(IllegalStateException.class,()->service.verify(query));
        binding.setFieldsJson("{\"originalFields\":{\"id\":\"10\"}}");
        when(bindings.selectSourceKeys(any())).thenReturn(List.of(binding));
        assertThrows(IllegalStateException.class,()->service.verify(query));verifyNoInteractions(plans);
    }
    @Test void changedOrDeletedBusinessFactCannotReplayOldEvidence() {
        doReturn(List.of(new Result("10","ISSUE",null,null,"deleted or changed"))).when(plans).preview(any());
        assertThrows(IllegalStateException.class,()->service.verify(query));
        doReturn(List.of()).when(plans).preview(any());
        assertThrows(IllegalStateException.class,()->service.verify(query));verify(plans,never()).apply(any());
    }
}
