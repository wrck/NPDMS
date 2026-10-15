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
    @Test void localContractSourceKeepsHistoryAndDoesNotPermitOtherIdentityChanges() {
        TenantContextHolder.setTenantId(1L);
        try {
            var mapper=mock(SyncTaskMapper.class);var bindings=mock(SyncBindingMapper.class);
            var service=new SyncTaskService(mapper,bindings,mock(SyncConnectionService.class),mock(SyncDefinitionValidator.class));
            var legacy=DppmsConsolidatedTemplate.create(new DppmsConsolidatedTemplate.Request("P15",18L));
            var sources=new java.util.ArrayList<>(legacy.sources());
            sources.set(0,sources.getFirst().toBuilder().readMode("LOCAL_OWNER").sourceObject("com_sales_order").sourceKey("migrationKey").build());
            var desired=legacy.toBuilder().sources(sources).build();
            var current=new SyncTaskDO().setId(20L).setVersion(6).setDefinition(JsonUtils.toJsonString(legacy));
            when(mapper.selectIdentity(any())).thenReturn(current);when(mapper.selectScoped(any())).thenReturn(current);
            when(bindings.hasTaskBindings(any())).thenReturn(true);
            assertTrue(service.checkConfiguration(new SyncTaskService.Save(20L,6,"delivery",desired)).allowed());
            assertFalse(service.checkConfiguration(new SyncTaskService.Save(20L,6,"delivery",desired.toBuilder().connectionId(19L).build())).allowed());
            sources.set(1,sources.get(1).toBuilder().sourceKey("differentKey").build());
            assertFalse(service.checkConfiguration(new SyncTaskService.Save(20L,6,"delivery",desired)).allowed());
            assertEquals(JsonUtils.toJsonString(legacy),current.getDefinition());verify(bindings,never()).deleteTaskBindings(any());
        } finally {TenantContextHolder.clear();}
    }
    @Test void approvedOrderLineKeyChangePreservesBindingsButStillRejectsOtherSourceChanges() {
        TenantContextHolder.setTenantId(1L);
        try {
            var mapper=mock(SyncTaskMapper.class);var bindings=mock(SyncBindingMapper.class);
            var validator=mock(SyncDefinitionValidator.class);
            var service=new SyncTaskService(mapper,bindings,mock(SyncConnectionService.class),validator);
            var desired=DppmsOrderSyncTemplate.create(18L);
            var oldLine=desired.sources().getLast();
            oldLine=oldLine.toBuilder().targets(java.util.List.of(oldLine.targets().getFirst()
                    .withKeys(java.util.List.of("order_id","line_no")))).build();
            var legacy=desired.toBuilder().sources(java.util.List.of(desired.sources().getFirst(),oldLine)).build();
            var current=new SyncTaskDO().setId(20L).setVersion(6).setDefinition(JsonUtils.toJsonString(legacy));
            when(mapper.selectIdentity(any())).thenReturn(current);when(mapper.selectScoped(any())).thenReturn(current);
            when(bindings.hasTaskBindings(any())).thenReturn(true);
            assertTrue(service.checkConfiguration(new SyncTaskService.Save(20L,6,"orders",desired)).allowed());
            assertFalse(service.checkConfiguration(new SyncTaskService.Save(20L,6,"orders",desired.toBuilder().connectionId(19L).build())).allowed());
            var changedLine=desired.sources().getLast().toBuilder().sourceKey("anotherKey").build();
            var changed=desired.toBuilder().sources(java.util.List.of(desired.sources().getFirst(),changedLine)).build();
            assertFalse(service.checkConfiguration(new SyncTaskService.Save(20L,6,"orders",changed)).allowed());
            verify(bindings,never()).deleteTaskBindings(any());
        } finally { TenantContextHolder.clear(); }
    }
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
