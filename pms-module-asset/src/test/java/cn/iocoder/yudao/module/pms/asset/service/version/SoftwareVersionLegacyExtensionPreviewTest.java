package cn.iocoder.yudao.module.pms.asset.service.version;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SoftwareVersionLegacyExtensionPreviewTest {
    EntityExtensionApi extensions=mock(EntityExtensionApi.class);PermissionApi permissions=mock(PermissionApi.class);
    SoftwareVersionLegacyExtensionService service=new SoftwareVersionLegacyExtensionService(extensions,permissions);
    Map<String,Object> values;
    @BeforeEach void setup(){TenantContextHolder.setTenantId(1L);values=new LinkedHashMap<>();SoftwareVersionLegacyExtensionService.CODES.forEach(n->values.put(n,null));
        when(extensions.definition(any(),any(),any())).thenReturn(new EntityExtensionApi.DefinitionRevision(99L,1L,"AST","SoftwareVersionRecord",1,SoftwareVersionLegacyExtensionService.definitions()));}
    @AfterEach void clear(){TenantContextHolder.clear();}
    @Test void newRecordPreviewChecksDefinitionWithoutCreatingValuesOrDefinitions(){
        service.preview(new SoftwareVersionLegacyExtensionService.Preview(1L,2L,-1L,99L,values));
        verify(extensions,never()).save(any());verify(extensions,never()).read(any(),any());verify(extensions,never()).publishDefinition(any(),any(),any(),any(),any());
    }
    @Test void wrongDefinitionAndInvalidFlagsFailWithoutWrite(){
        values.put("conp_change",2);assertThrows(IllegalArgumentException.class,()->service.preview(new SoftwareVersionLegacyExtensionService.Preview(1L,2L,-1L,99L,values)));
        values.put("conp_change",null);when(extensions.definition(any(),any(),any())).thenReturn(new EntityExtensionApi.DefinitionRevision(99L,2L,"AST","SoftwareVersionRecord",1,SoftwareVersionLegacyExtensionService.definitions()));
        assertThrows(IllegalArgumentException.class,()->service.preview(new SoftwareVersionLegacyExtensionService.Preview(1L,2L,-1L,99L,values)));verify(extensions,never()).save(any());
    }
    @Test void replayPreviewChecksFrozenExtensionValues(){
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(99L,new LinkedHashMap<>(values),1));
        service.preview(new SoftwareVersionLegacyExtensionService.Preview(1L,2L,11L,99L,values));values.put("conp_bak","changed");
        assertThrows(IllegalStateException.class,()->service.preview(new SoftwareVersionLegacyExtensionService.Preview(1L,2L,11L,99L,values)));verify(extensions,never()).save(any());
    }
}
