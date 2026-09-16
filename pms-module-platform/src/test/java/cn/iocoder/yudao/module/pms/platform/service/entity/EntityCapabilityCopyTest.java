package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.query.EntityValueQuery;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityCapabilityCopyTest {
    private final EntityCapabilityMapper mapper = mock(EntityCapabilityMapper.class);
    private final EntityProviderRegistry registry = mock(EntityProviderRegistry.class);
    private final OperationAuditApi audit = mock(OperationAuditApi.class);
    private final EntityRef entity = new EntityRef(7L, "SOL", "REQUIREMENT_ANALYSIS", 11L);
    private final EntityActor actor = new EntityActor(7L, 1L, "copy-test");
    private final EntityDataRef source = EntityDataRef.revision(new RevisionRef(entity, 21L));
    private final EntityDataRef current = EntityDataRef.current(entity);
    private final EntityExtensionService extensions = new EntityExtensionService(mapper, registry, mock(PermissionApi.class), audit);

    @Test void activatingRevisionWithoutExtensionsClearsOnlyMutableTarget() {
        var values = new EntityExtensionValueDO();
        values.setDefinitionRevisionId(1L);
        values.setValuesJson("{\"oldField\":\"old value\"}");
        values.setVersion(2);
        when(mapper.lockValues(EntityValueQuery.of(current))).thenReturn(values);
        when(mapper.deleteValues(values)).thenReturn(1);
        extensions.copy(source, current, 3, actor);
        var order = inOrder(registry, mapper);
        order.verify(registry).lockForWrite(current, actor, 3);
        order.verify(mapper).lockValues(EntityValueQuery.of(current));
        order.verify(mapper).deleteValues(values);
        verify(mapper, never()).deleteValues(argThat(row -> row != values));
    }

    @Test void emptySourceCannotBypassFrozenTargetProtection() {
        var frozen = EntityDataRef.revision(new RevisionRef(entity, 31L));
        doThrow(new IllegalStateException("frozen")).when(registry).lockForWrite(frozen, actor, 3);
        assertThrows(IllegalStateException.class, () -> extensions.copy(source, frozen, 3, actor));
        verify(mapper, never()).lockValues(EntityValueQuery.of(frozen));
        verify(mapper, never()).deleteValues(any());
    }

    @Test void requiredDefinitionStillAppliesWhenNoValueRowHasEverBeenSaved() {
        var definition = new EntityExtensionDefinitionDO();
        definition.setId(1L); definition.setTenantId(7L); definition.setOwnerModule(entity.ownerModule());
        definition.setEntityType(entity.entityType()); definition.setRevisionNo(1);
        definition.setFieldsJson(JsonUtils.toJsonString(List.of(new EntityExtensionApi.Definition(
                "requiredField", "Required", EntityField.Type.TEXT, true, null, List.of()))));
        var binding = new EntityFormBindingDO(); binding.setExtensionDefinitionRevisionId(1L);
        when(mapper.selectBinding(EntityValueQuery.of(source))).thenReturn(binding);
        when(mapper.selectDefinition(1L)).thenReturn(definition);
        assertThrows(cn.iocoder.yudao.framework.common.exception.ServiceException.class,
                () -> extensions.validateComplete(source, actor));
    }

    @Test void activatingFormlessRevisionDoesNotKeepPreviousCurrentLayout() {
        var binding = new EntityFormBindingDO(); binding.setVersion(4);
        when(mapper.lockBinding(EntityValueQuery.of(current))).thenReturn(binding);
        when(mapper.deleteBinding(binding)).thenReturn(1);
        var forms = new EntityFormService(mapper, registry, extensions, mock(DynamicFormTemplateRevisionMapper.class),
                mock(DynamicFormSchemaService.class), mock(DynamicFormBusinessInstanceApi.class), audit);
        forms.copy(source, current, 3, actor);
        verify(registry).lockForWrite(current, actor, 3);
        verify(mapper).deleteBinding(binding);
    }
}
