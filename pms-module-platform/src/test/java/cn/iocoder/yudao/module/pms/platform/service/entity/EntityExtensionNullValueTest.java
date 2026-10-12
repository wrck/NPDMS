package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.EntityExtensionDefinitionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityExtensionNullValueTest {
    @Test void explicitNullBackupSurvivesActualSaveAndReadWithSharedNonNullJsonMapper() {
        var mapper=mock(EntityCapabilityMapper.class);
        var registry=mock(EntityProviderRegistry.class);
        var ref=new EntityRef(1L,"AST","SoftwareVersionRecord",55L);
        var target=EntityDataRef.current(ref);
        var actor=new EntityActor(1L,7L,"legacy-history");
        when(registry.nativeRef(target)).thenReturn(target);
        when(registry.nativeRef(ref)).thenReturn(ref);
        var provider=mock(EntityFieldProvider.class);
        when(provider.fields()).thenReturn(List.of());
        when(registry.fields(ref)).thenReturn(provider);
        var definitions=List.of(
                new EntityExtensionApi.Definition("pcb_bak","PCB原备份",EntityField.Type.TEXT,false,255,List.of()),
                new EntityExtensionApi.Definition("boot_bak","BOOT原备份",EntityField.Type.TEXT,false,255,List.of()),
                new EntityExtensionApi.Definition("boot_change","BOOT原变化标志",EntityField.Type.NUMBER,false,null,List.of()));
        var definition=new EntityExtensionDefinitionDO();
        definition.setId(99L);definition.setTenantId(1L);definition.setOwnerModule("AST");
        definition.setEntityType("SoftwareVersionRecord");definition.setRevisionNo(1);
        definition.setFieldsJson(JsonUtils.toJsonString(definitions));
        when(mapper.selectDefinition(99L)).thenReturn(definition);
        var api=new EntityExtensionService(mapper,registry,mock(PermissionApi.class),mock(OperationAuditApi.class));
        Map<String,Object> values=new LinkedHashMap<>();values.put("pcb_bak",null);values.put("boot_bak","");values.put("boot_change",0);
        var saved=api.save(new EntityExtensionApi.Save(target,actor,0L,0,99L,values));
        assertEquals(values.keySet(),saved.fields().keySet());
        assertNull(saved.fields().get("pcb_bak"));assertEquals("",saved.fields().get("boot_bak"));assertEquals(0,saved.fields().get("boot_change"));
        verify(mapper).insertValues(argThat(row -> row.getValuesJson().contains("\"pcb_bak\":null")));
        verify(registry).lockForWrite(target,actor,0L);
    }
}
