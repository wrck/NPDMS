package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceSoftwareVersionRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceSoftwareVersionRecordMapper;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class SoftwareVersionRecordEntityProviderTest {
    final DeviceSoftwareVersionRecordMapper records=mock(DeviceSoftwareVersionRecordMapper.class);
    final DeviceAccessScopeService access=mock(DeviceAccessScopeService.class);
    final PermissionApi permissions=mock(PermissionApi.class);
    final SoftwareVersionRecordEntityProvider provider=new SoftwareVersionRecordEntityProvider(records,access,permissions);
    final EntityRef ref=new EntityRef(1L,"AST","SoftwareVersionRecord",55L);
    final EntityDataRef target=EntityDataRef.current(ref);
    final EntityActor actor=new EntityActor(1L,7L,"read");
    DeviceSoftwareVersionRecordDO row;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);row=new DeviceSoftwareVersionRecordDO();row.setId(55L);row.setTenantId(1L);row.setDeviceId(8L);row.setProjectId(10L);row.setDataSource("DPPMS");
        when(records.selectById(55L)).thenReturn(row);when(records.selectForUpdate(any())).thenReturn(row);
        when(permissions.hasAnyPermissions(eq(7L),any(String[].class))).thenReturn(true);
    }
    @AfterEach void clear() {TenantContextHolder.clear();}
    @Test void genericWriteCannotEditEvenWithIntegrationPermissions() {
        assertThrows(IllegalArgumentException.class,()->provider.lockForWrite(target,actor,0L));verify(records,never()).selectForUpdate(any());
    }
    @Test void historyUsesCurrentDeviceVisibilityAndRejectsUnauthorizedDevices() {
        var query=new DeviceAccessScopeService.ReadAccess(1L,7L,8L);
        provider.requireReadable(target,actor);verify(access).assertReadable(query);
        doThrow(new IllegalArgumentException("device not visible")).when(access).assertReadable(query);
        assertThrows(IllegalArgumentException.class,()->provider.requireReadable(target,actor));
    }
    @Test void observerForeignTenantAndRevisionCannotReadHistory() {
        assertThrows(IllegalArgumentException.class,()->provider.requireReadable(target,new EntityActor(1L,0L,EntityActor.SYSTEM_OBSERVER)));
        assertThrows(IllegalArgumentException.class,()->provider.requireReadable(new EntityDataRef(ref,9L),actor));
        TenantContextHolder.setTenantId(2L);assertThrows(IllegalArgumentException.class,()->provider.requireReadable(target,actor));
    }
    @Test void importInitializationRequiresExactRecordAndAlwaysClearsServerScope() {
        SoftwareVersionRecordEntityProvider.initialize(ref,()->{provider.lockForWrite(target,actor,0L);return null;});
        assertThrows(IllegalArgumentException.class,()->provider.lockForWrite(target,actor,0L));
        row.setDataSource("PLATFORM_MANUAL");
        assertThrows(IllegalArgumentException.class,()->SoftwareVersionRecordEntityProvider.initialize(ref,()->{provider.lockForWrite(target,actor,0L);return null;}));
        assertThrows(IllegalArgumentException.class,()->provider.lockForWrite(target,actor,0L));
    }
    @Test void replayPreservesEmptyStringNullAndZeroFlagsAndRejectsDifferentImmutableValues() {
        var api=mock(EntityExtensionApi.class);var service=new SoftwareVersionLegacyExtensionService(api,permissions);
        when(api.definition(eq(99L),any(),any())).thenReturn(new EntityExtensionApi.DefinitionRevision(99L,1L,"AST","SoftwareVersionRecord",1,SoftwareVersionLegacyExtensionService.definitions()));
        var values=new LinkedHashMap<String,Object>();for(String code:SoftwareVersionLegacyExtensionService.CODES)values.put(code,code.endsWith("_bak")?"":0);
        values.put("pcb_bak",null);
        when(api.read(any(),any())).thenReturn(new EntityExtensionApi.Values(99L,values,1));
        assertEquals(1,service.initialize(1L,7L,55L,99L,values).version());verify(api,never()).save(any());
        var modified=new LinkedHashMap<>(values);modified.put("conp_change",1);
        assertThrows(IllegalStateException.class,()->service.initialize(1L,7L,55L,99L,modified));
        assertThrows(IllegalArgumentException.class,()->provider.lockForWrite(target,actor,0L));
    }
    @Test void definitionConfigurationUsesOnlyApprovedEightPropertiesAndRequiresBothPermissions() {
        var api=mock(EntityExtensionApi.class);var service=new SoftwareVersionLegacyExtensionService(api,permissions);
        var definition=new EntityExtensionApi.DefinitionRevision(99L,1L,"AST","SoftwareVersionRecord",1,SoftwareVersionLegacyExtensionService.definitions());
        when(api.publishDefinition(eq(1L),eq("AST"),eq("SoftwareVersionRecord"),any(),any())).thenReturn(definition);
        assertEquals(99L,service.configureDefinition(1L,7L,null));
        verify(api).publishDefinition(eq(1L),eq("AST"),eq("SoftwareVersionRecord"),eq(SoftwareVersionLegacyExtensionService.definitions()),any());
        when(permissions.hasAnyPermissions(7L,"pms:integration:configure")).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->service.configureDefinition(1L,7L,null));
        verify(api,times(1)).publishDefinition(any(),any(),any(),any(),any());
    }
    @Test void unapprovedDefinitionCannotInitializeHistoricalExtensions() {
        var api=mock(EntityExtensionApi.class);var service=new SoftwareVersionLegacyExtensionService(api,permissions);
        when(api.definition(eq(99L),any(),any())).thenReturn(new EntityExtensionApi.DefinitionRevision(99L,1L,"AST","SoftwareVersionRecord",1,List.of()));
        var values=new LinkedHashMap<String,Object>();for(String code:SoftwareVersionLegacyExtensionService.CODES)values.put(code,null);
        assertThrows(IllegalArgumentException.class,()->service.initialize(1L,7L,55L,99L,values));
        verify(api,never()).read(any(),any());verify(api,never()).save(any());
    }
}
