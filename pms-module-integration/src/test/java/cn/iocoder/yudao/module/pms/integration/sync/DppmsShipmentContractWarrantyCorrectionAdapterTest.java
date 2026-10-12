package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ShipmentContractWarrantyCorrectionApi;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsShipmentContractWarrantyCorrectionAdapterTest {
    ShipmentContractWarrantyCorrectionApi references=mock(ShipmentContractWarrantyCorrectionApi.class);
    SyncTaskService tasks=mock(SyncTaskService.class); SyncRunMapper runs=mock(SyncRunMapper.class);
    DppmsShipmentContractWarrantyCorrectionAdapter adapter=new DppmsShipmentContractWarrantyCorrectionAdapter(references,tasks,runs);
    Map<String,Object> fields; SyncRunDO run;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        when(tasks.required(12L)).thenReturn(new SyncTaskDO().setId(12L)
                .setAdapter(DppmsShipmentContractWarrantyCorrectionAdapter.ADAPTER).setSourceSystem("DPPMS").setVersion(0).setActiveRunId(13L));
        run=new SyncRunDO().setId(13L).setTaskId(12L).setConfigVersion(0).setPreview(true).setStatus("VALIDATING");
        when(runs.selectScoped(any())).thenReturn(run);
        fields=new LinkedHashMap<>();DppmsShipmentContractWarrantyCorrectionAdapter.SOURCE_COLUMNS.forEach(c->fields.put(c,null));
        fields.put("contract_id","C");fields.put("warranty","  ");
        String source="{"+fields.entrySet().stream().map(e->JsonUtils.toJsonString(e.getKey())+":"
                +(e.getValue()==null?"null":JsonUtils.toJsonString(e.getValue()))).collect(java.util.stream.Collectors.joining(","))+"}";
        fields.put("correctionEvidence",JsonUtils.toJsonString(List.of(new DppmsShipmentContractWarrantyCorrectionAdapter.Proof(
                77L,"C","a".repeat(64),"b".repeat(64),source))));
        var result=new ShipmentContractWarrantyCorrectionApi.Result(77L,"C","  ","UPDATED","a".repeat(64),"b".repeat(64));
        when(references.preview(any())).thenReturn(List.of(result));when(references.apply(any())).thenReturn(List.of(result));
    }
    @AfterEach void cleanup(){TenantContextHolder.clear();}
    Batch batch(){return new Batch("integration:1:12",List.of(new Row("SHIPMENT_CONTRACT","C",fields,null)),List.of(),true,"RETAIN",true,"UPSERT",false);}
    @Test void validPreviewAndApplyUseFormalOwnerAndKeepEvidence() {
        assertEquals("UPDATED",adapter.preview(batch()).getFirst().action());verify(references,never()).apply(any());
        run.setPreview(false).setStatus("APPLYING");var changed=adapter.apply(batch()).getFirst();
        assertEquals(77L,changed.targetId());assertTrue(changed.after().containsKey("warrantyYears"));assertNull(changed.after().get("warrantyYears"));
        verify(references,times(1)).apply(any());
    }
    @Test void changedOrMissingOriginalFieldsBlockOwnerInvocation() {
        fields.put("customer_name","later change");assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));
        fields.put("customer_name",null);fields.remove("remark");assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));
        verifyNoInteractions(references);
    }
    @Test void wrongRunVersionStageOrOwnerBlocksCorrection() {
        run.setConfigVersion(1);assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));
        run.setConfigVersion(0).setStatus("SUCCESS");assertThrows(IllegalArgumentException.class,()->adapter.preview(batch()));
        var b=batch();assertThrows(IllegalArgumentException.class,()->adapter.preview(new Batch("integration:2:12",b.rows(),b.bindings(),true,"RETAIN",true,"UPSERT",false)));
        verifyNoInteractions(references);
    }
}
