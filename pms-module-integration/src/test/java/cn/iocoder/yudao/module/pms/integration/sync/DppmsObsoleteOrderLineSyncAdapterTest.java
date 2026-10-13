package cn.iocoder.yudao.module.pms.integration.sync;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.OrderLineMigrationPresenceApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DppmsObsoleteOrderLineSyncAdapterTest {
    SyncTaskService tasks=mock(SyncTaskService.class);SyncTaskMapper taskMapper=mock(SyncTaskMapper.class);
    SyncRunMapper runs=mock(SyncRunMapper.class);SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    OrderLineMigrationPresenceApi lines=mock(OrderLineMigrationPresenceApi.class);
    DppmsObsoleteOrderLineSyncAdapter adapter=new DppmsObsoleteOrderLineSyncAdapter(tasks,taskMapper,runs,bindings,lines);
    SyncTaskDO sourceTask;SyncBindingDO old;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);var task=new SyncTaskDO();task.setId(20L);task.setTenantId(1L);task.setVersion(0);
        task.setAdapter("DPPMS_OBSOLETE_ORDER_LINE_BINDING");task.setSourceSystem("DPPMS");task.setConnectionId(11L);task.setActiveRunId(31L);
        task.setDefinition(JsonUtils.toJsonString(SyncDefinition.builder().mode("ONCE").build()));when(tasks.required(20L)).thenReturn(task);
        var run=new SyncRunDO();run.setId(31L);run.setTenantId(1L);run.setTaskId(20L);run.setConfigVersion(0);run.setPreview(false);
        run.setStatus("APPLYING");run.setCreator("7");when(runs.selectScoped(any())).thenReturn(run);
        sourceTask=new SyncTaskDO();sourceTask.setId(9L);sourceTask.setTenantId(1L);sourceTask.setAdapter("DPPMS_ERP_ORDER");
        sourceTask.setSourceSystem("DPPMS");sourceTask.setConnectionId(11L);sourceTask.setDeleted(false);
        when(taskMapper.selectForUpdate(any())).thenReturn(sourceTask);
        old=new SyncBindingDO();old.setId(101L);old.setTenantId(1L);old.setTaskId(9L);old.setTargetId(71L);old.setSourceKey("11");
        when(bindings.selectObsoleteOrderLinesForUpdate(any())).thenReturn(List.of(old));when(lines.existingTargets(any())).thenReturn(Set.of());
        when(bindings.deleteObsoleteOrderLines(any())).thenReturn(1);
    }
    @AfterEach void close(){TenantContextHolder.clear();}
    Batch batch(){return new Batch("integration:1:20",List.of(new Row("pm_order_line_from_erp","11",
            Map.of("id","11","compCode","01","_originalTaskId","9"),null)),List.of(),true,"RETAIN",false,"UPSERT",false);}
    @Test void missingTargetBindingCanBeRemovedWithoutCreatingReplacementBinding() {
        var change=adapter.apply(batch()).getFirst();assertEquals("DELETED",change.action());assertNull(change.targetId());
        verify(bindings).deleteObsoleteOrderLines(new SyncQueries.ObsoleteOrderLineDelete(1L,9L,List.of(101L)));
    }
    @Test void anyExistingFormalTargetBlocksDeletion() {
        when(lines.existingTargets(any())).thenReturn(Set.of(71L));
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch()));
        verify(bindings,never()).deleteObsoleteOrderLines(any());
    }
    @Test void runningOriginalTaskBlocksCleanup() {
        sourceTask.setActiveRunId(44L);
        assertThrows(IllegalArgumentException.class,()->adapter.apply(batch()));verifyNoInteractions(lines);
        verify(bindings,never()).deleteObsoleteOrderLines(any());
    }
    @Test void replayHasNoDestructiveEffect() {
        when(bindings.selectObsoleteOrderLinesForUpdate(any())).thenReturn(List.of());
        assertEquals("SKIPPED",adapter.apply(batch()).getFirst().action());verify(bindings,never()).deleteObsoleteOrderLines(any());
    }
}
