package cn.iocoder.yudao.module.pms.platform.service.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform.DynamicFormTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.EntityExtensionDefinitionDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.DynamicFormTemplateRevisionMapper;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.entity.EntityCapabilityMapper;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormSchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntityFormBindingTest {
    private final EntityCapabilityMapper mapper = mock(EntityCapabilityMapper.class);
    private final EntityProviderRegistry registry = mock(EntityProviderRegistry.class);
    private final DynamicFormTemplateRevisionMapper revisions = mock(DynamicFormTemplateRevisionMapper.class);
    private final DynamicFormBusinessInstanceApi forms = mock(DynamicFormBusinessInstanceApi.class);
    private final OperationAuditApi audit = mock(OperationAuditApi.class);
    private final EntityExtensionService extensions = new EntityExtensionService(mapper, registry, null, audit);
    private final EntityFormService service = new EntityFormService(mapper, registry, extensions, revisions, new DynamicFormSchemaService(), forms, audit);
    private final EntityDataRef target = EntityDataRef.current(new EntityRef(1L, "SOL", "SITE_SURVEY", 5L));
    private final EntityActor actor = new EntityActor(1L, 9L, null);
    private final DynamicFormTemplateRevisionDO revision = new DynamicFormTemplateRevisionDO();
    private final List<EntityExtensionApi.Definition> definitions = List.of(new EntityExtensionApi.Definition("tags", "tags", EntityField.Type.TEXT_LIST, false, null, List.of("A", "B")));

    @BeforeEach void prepare() {
        var provider = mock(EntityFieldProvider.class);
        when(registry.fields(any())).thenReturn(provider);
        when(provider.fields()).thenReturn(List.of(new EntityField("powerSupply", EntityField.Type.TEXT, false)));
        when(provider.formUsage()).thenReturn("SITE_SURVEY");
        revision.setId(10L); revision.setTenantId(1L); revision.setVersion(1); revision.setStatusCode("PUBLISHED");
        revision.setEngineCode(DynamicFormSchemaService.ENGINE_CODE);
        revision.setDesignerVersion(DynamicFormSchemaService.DESIGNER_VERSION);
        revision.setRendererVersion(DynamicFormSchemaService.RENDERER_VERSION);
        revision.setFormConfJson("{}");
        revision.setFormRulesJson("[{\"type\":\"input\",\"field\":\"power\"},{\"type\":\"checkbox\",\"field\":\"tags\",\"options\":[{\"value\":\"A\"},{\"value\":\"B\"}]}]");
        when(revisions.selectByRow(any())).thenReturn(revision);
        when(mapper.selectMaxDefinitionRevision(any())).thenReturn(2);
        doAnswer(invocation -> {
            EntityExtensionDefinitionDO row = invocation.getArgument(0);
            when(mapper.selectDefinition(row.getId())).thenReturn(row);
            return 1;
        }).when(mapper).insertDefinition(any());
    }

    private EntityFormApi.Bind command() {
        return new EntityFormApi.Bind(target, actor, 1, 0, 10L, null, Map.of("power", "powerSupply", "tags", "tags"));
    }

    @Test void firstBindingCreatesTypedDefinitionAndSaveAcceptsOnlyDeclaredValues() {
        var binding = service.bind(command());
        assertNotNull(binding.extensionDefinitionRevisionId());
        assertEquals(definitions, extensions.definition(binding.extensionDefinitionRevisionId(), target.entity(), actor).fields());
        var saved = extensions.save(new EntityExtensionApi.Save(target, actor, 1, 0,
                binding.extensionDefinitionRevisionId(), Map.of("tags", List.of("A", "B"))));
        assertEquals(List.of("A", "B"), saved.fields().get("tags"));
        assertThrows(RuntimeException.class, () -> extensions.save(new EntityExtensionApi.Save(target, actor, 1, 0,
                binding.extensionDefinitionRevisionId(), Map.of("tags", List.of("INVALID")))));
    }

    @Test void reusesMigratedDefinitionWithoutOverwritingIt() {
        var row = new EntityExtensionDefinitionDO(); row.setId(11L); row.setTenantId(1L);
        row.setOwnerModule("SOL"); row.setEntityType("SITE_SURVEY"); row.setRevisionNo(1);
        row.setFieldsJson(JsonUtils.toJsonString(definitions));
        when(mapper.selectDefinitionBySource(any())).thenReturn(row);
        when(mapper.selectDefinition(11L)).thenReturn(row);
        assertEquals(11L, service.bind(command()).extensionDefinitionRevisionId());
        verify(mapper, never()).insertDefinition(any());
        row.setFieldsJson("[]");
        assertThrows(RuntimeException.class, () -> service.bind(command()));
    }

    @Test void fixedOnlyFormDoesNotCreateExtensionDefinition() {
        var binding = service.bind(new EntityFormApi.Bind(target, actor, 1, 0, 10L, null, Map.of("power", "powerSupply")));
        assertNull(binding.extensionDefinitionRevisionId());
        verify(mapper, never()).insertDefinition(any());
    }

    @Test void rejectedOwnerWriteAndUnpublishedTemplateCannotCreateDefinitions() {
        doThrow(new IllegalStateException("frozen")).when(registry).lockForWrite(any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.bind(command()));
        verifyNoInteractions(revisions);
        verify(mapper, never()).insertDefinition(any());
        doNothing().when(registry).lockForWrite(any(), any(), any());
        revision.setStatusCode("DRAFT");
        assertThrows(RuntimeException.class, () -> service.bind(command()));
        verify(mapper, never()).insertDefinition(any());
    }
}
