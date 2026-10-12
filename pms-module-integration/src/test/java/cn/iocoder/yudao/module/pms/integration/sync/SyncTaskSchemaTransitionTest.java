package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.sync.generic.GenericSyncTemplates;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SyncTaskSchemaTransitionTest {
    @Test void replacesRetiredGenericColumnsWithoutRevalidatingHistoryAndStillGuardsIdentity() {
        TenantContextHolder.setTenantId(1L);
        try {
            var mapper=mock(SyncTaskMapper.class);var bindings=mock(SyncBindingMapper.class);
            var validator=mock(SyncDefinitionValidator.class);
            var service=new SyncTaskService(mapper,bindings,mock(SyncConnectionService.class),validator);
            var desired=new GenericSyncTemplates().list(18L).getFirst().definition();
            var legacy=JsonUtils.parseObject(JsonUtils.toJsonString(desired).replace("warranty_years","warranty_flag")
                    .replace("DPPMS_WARRANTY_YEARS","STRING"),SyncDefinition.class);
            var current=new SyncTaskDO().setId(20L).setName("contracts").setVersion(4).setDefinition(JsonUtils.toJsonString(legacy));
            when(mapper.selectIdentity(any())).thenReturn(current);when(mapper.selectScoped(any())).thenReturn(current);
            when(bindings.hasTaskBindings(any())).thenReturn(true);
            when(validator.adapter(legacy)).thenThrow(new IllegalArgumentException("retired field"));
            assertTrue(service.checkConfiguration(new SyncTaskService.Save(20L,4,"contracts",desired)).allowed());
            verify(validator).validate(desired);verify(validator,never()).adapter(legacy);
            assertFalse(service.checkConfiguration(new SyncTaskService.Save(20L,4,"contracts",desired.toBuilder().connectionId(19L).build())).allowed());
            assertFalse(service.checkConfiguration(new SyncTaskService.Save(20L,3,"contracts",desired)).allowed());
        } finally { TenantContextHolder.clear(); }
    }
}
