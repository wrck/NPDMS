package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractReceivableMigrationApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DppmsContractReceivableSyncAdapterTest {
    ContractReceivableMigrationApi owner=mock(ContractReceivableMigrationApi.class);
    SystemUserMigrationApi users=mock(SystemUserMigrationApi.class);
    SyncTaskService tasks=mock(SyncTaskService.class);
    SyncRunMapper runs=mock(SyncRunMapper.class);
    DppmsContractReceivableSyncAdapter adapter=new DppmsContractReceivableSyncAdapter(owner,users,tasks,runs);

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var task=new SyncTaskDO();task.setId(20L);task.setTenantId(1L);task.setVersion(2);
        task.setAdapter("DPPMS_CONTRACT_RECEIVABLE");task.setSourceSystem("DPPMS");task.setActiveRunId(31L);
        task.setDefinition(JsonUtils.toJsonString(DppmsContractReceivableSyncTemplate.create(11L)));
        when(tasks.required(20L)).thenReturn(task);
        var run=new SyncRunDO();run.setId(31L);run.setTenantId(1L);run.setTaskId(20L);run.setConfigVersion(2);
        run.setPreview(true);run.setStatus("VALIDATING");run.setCreator("7");
        when(runs.selectScoped(any())).thenReturn(run);
    }
    @AfterEach void close(){TenantContextHolder.clear();}
    Batch batch(int count) {
        var rows=new ArrayList<Row>();
        for(int i=1;i<=count;i++) {
            var original=new LinkedHashMap<String,Object>();
            ContractReceivableMigrationApi.ORIGINAL_COLUMNS.forEach(k->original.put(k,null));
            original.put("id",i);original.put("contract_num","C"+i);
            original.put("create_by","creator"+i);original.put("update_by","updater"+i);
            rows.add(new Row("sms_ofst_contract_head_sap",Integer.toString(i),original,null));
        }
        return new Batch("integration:1:20",rows,List.of(),true,"RETAIN",false,"UPSERT",false);
    }
    @Test void largeChunkLooksUpEveryAuditActorWithinSystemLimitAndOnlyPreviewsOwner() {
        var lookedUp=new HashSet<String>();
        when(users.getHistoricalIdentitiesByUsername(eq(1L),anySet())).thenAnswer(call->{
            Set<String> names=call.getArgument(1);assertTrue(names.size()<=200);
            for(String name:names)assertTrue(lookedUp.add(name),"actor queried twice");
            return List.of();
        });
        when(owner.preview(any())).thenAnswer(call->{
            var request=(ContractReceivableMigrationApi.Request)call.getArgument(0);
            assertEquals(1L,request.tenantId());assertEquals(7L,request.actorId());
            assertEquals(500,request.entries().size());
            return request.entries().stream().map(entry->new ContractReceivableMigrationApi.Result(
                    entry.sourceKey(),null,"CREATED",Map.of(),"proof")).toList();
        });
        assertEquals(500,adapter.preview(batch(500)).size());assertEquals(1000,lookedUp.size());
        verify(users,times(5)).getHistoricalIdentitiesByUsername(eq(1L),anySet());
        verify(owner,never()).apply(any());
    }
    @Test void failedAuditLookupNeverReachesBusinessOwner() {
        when(users.getHistoricalIdentitiesByUsername(anyLong(),anySet())).thenThrow(new IllegalArgumentException("ambiguous audit identity"));
        assertThrows(IllegalArgumentException.class,()->adapter.preview(batch(500)));
        verifyNoInteractions(owner);
    }
}
