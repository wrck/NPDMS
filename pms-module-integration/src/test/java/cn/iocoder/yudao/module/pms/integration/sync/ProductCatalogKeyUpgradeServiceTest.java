package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.SyncTaskDO;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.sync.generic.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProductCatalogKeyUpgradeServiceTest {
    SyncTaskMapper tasks=mock(SyncTaskMapper.class);
    SyncBindingMapper bindings=mock(SyncBindingMapper.class);
    SyncConnectionService connections=mock(SyncConnectionService.class);
    SyncDefinitionValidator validator=new SyncDefinitionValidator(List.of());
    SyncTaskService service=new SyncTaskService(tasks,bindings,connections,validator);
    SyncTaskDO current=new SyncTaskDO();SyncDefinition desired,previous;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        var engine=new GenericSyncEngine(new GenericTargetCatalog(),mock(GenericSyncJdbcStore.class),mock(org.springframework.context.ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(validator,"genericEngine",engine);
        desired=new GenericSyncTemplates().list(1L).stream().filter(t->t.key().equals("crm-product")).findFirst().orElseThrow().definition();
        var source=desired.sources().getFirst();var step=source.targets().getFirst();
        var old=new GenericTargetStep(step.name(),step.table(),List.of("source_system","source_key"),step.mode(),step.nullPolicy(),step.updateColumns(),step.whenField(),step.newerBy(),step.tieBreaker(),step.mappings(),step.lookups());
        previous=desired.toBuilder().sources(List.of(source.toBuilder().targets(List.of(old)).build())).build();
        current.setId(10L);current.setTenantId(1L);current.setVersion(1);current.setDefinition(JsonUtils.toJsonString(previous));
        current.setName("Product");current.setEnabled(false);current.setCheckpoint(java.time.LocalDateTime.of(2026,1,1,0,0));
        when(tasks.selectScoped(any())).thenReturn(current);when(tasks.selectForUpdate(any())).thenReturn(current);
        when(tasks.selectIdentity(any())).thenReturn(current);when(bindings.hasTaskBindings(any())).thenReturn(true);
    }
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void formalSaveAllowsOnlyApprovedKeyUpgradeAndKeepsEstablishedSourceBindingsAndCheckpoint() {
        var before=current.getDefinition();var checkpoint=current.getCheckpoint();
        assertTrue(service.checkConfiguration(new SyncTaskService.Save(10L,1,"Product",desired)).allowed());
        assertEquals(before,current.getDefinition());assertEquals(previous,JsonUtils.parseObject(before,SyncDefinition.class));
        assertEquals(10L,service.save(new SyncTaskService.Save(10L,1,"Product",desired)));
        assertEquals(2,current.getVersion());assertEquals(desired,JsonUtils.parseObject(current.getDefinition(),SyncDefinition.class));
        assertEquals(checkpoint,current.getCheckpoint());assertFalse(current.getEnabled());assertNull(current.getValidatedVersion());
        verify(bindings,never()).delete(any());
    }
    @Test void changingSourcePrimaryKeyOrConnectionStillCannotUseTheTargetKeyUpgradeToAdoptMappings() {
        var source=desired.sources().getFirst().toBuilder().sourceKey("OtherKey").build();
        assertFalse(service.checkConfiguration(new SyncTaskService.Save(10L,1,"Product",desired.toBuilder().sources(List.of(source)).build())).allowed());
        assertFalse(service.checkConfiguration(new SyncTaskService.Save(10L,1,"Product",desired.toBuilder().connectionId(2L).build())).allowed());
        verify(tasks,never()).updateById(any(SyncTaskDO.class));
    }
    @Test void activeRunAndStaleVersionStillRejectSaveWithoutAnyDefinitionMutation() {
        var before=current.getDefinition();current.setActiveRunId(999L);
        assertThrows(IllegalArgumentException.class,()->service.save(new SyncTaskService.Save(10L,1,"Product",desired)));
        current.setActiveRunId(null);assertThrows(IllegalArgumentException.class,()->service.save(new SyncTaskService.Save(10L,0,"Product",desired)));
        assertEquals(before,current.getDefinition());assertEquals(1,current.getVersion());verify(tasks,never()).updateById(any(SyncTaskDO.class));
    }
}
