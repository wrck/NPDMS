package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SyncBindingRestorationTest {
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void engineUpdatesRestoredBindingInsteadOfInsertingTheSameSourceAgain() {
        TenantContextHolder.setTenantId(1L);
        var bindings=mock(SyncBindingMapper.class);var jdbc=mock(GenericSyncJdbcStore.class);
        var validator=mock(SyncDefinitionValidator.class);var adapter=mock(DataSyncAdapter.class);
        when(validator.adapter(any(SyncDefinition.class))).thenReturn(adapter);when(adapter.restoresSourceBindings()).thenReturn(true);
        var runner=new SyncRunService(null,null,null,bindings,jdbc,null,null,null,null,validator,null,null,null,null);
        var definition=DppmsProjectMasterSyncTemplate.create(18L);var object=DppmsProjectMasterSyncTemplate.OBJECT;
        var task=new SyncTaskDO().setId(12L);task.setTenantId(1L);
        var restored=new SyncBindingDO().setId(91L).setTaskId(12L).setObjectKey(object).setSourceObject(object)
                .setSourceKey("10").setTargetId(40L).setTargetShared(false).setFieldsJson("{\"v\":1}");restored.setTenantId(1L);
        when(bindings.selectSourceKeys(any())).thenReturn(List.of(restored));
        var change=new DataSyncAdapter.Change(object,"10",40L,"UPDATED",Map.of("v",1),Map.of("v",2),null);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(runner,"persistBindings",task,definition,List.of(change),13L,List.of());
        verify(bindings).updateBatch(argThat(rows->rows.size()==1&&rows.iterator().next().getId().equals(91L)&&rows.iterator().next().getLastRunId().equals(13L)),eq(1000));
        verifyNoInteractions(jdbc);assertEquals("{\"v\":2}",restored.getFieldsJson());
    }
}
