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
        when(registry.nativeRef(any(EntityDataRef.class))).thenAnswer(call->call.getArgument(0));
        when(registry.nativeRef(any(EntityRef.class))).thenAnswer(call->call.getArgument(0));
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
        return new EntityFormApi.Bind(target, actor, 1L, 0, 10L, null, Map.of("power", "powerSupply", "tags", "tags"));
    }

    @Test void firstBindingCreatesTypedDefinitionAndSaveAcceptsOnlyDeclaredValues() {
        var binding = service.bind(command());
        assertNotNull(binding.extensionDefinitionRevisionId());
        assertEquals(definitions, extensions.definition(binding.extensionDefinitionRevisionId(), target.entity(), actor).fields());
        var saved = extensions.save(new EntityExtensionApi.Save(target, actor, 1L, 0,
                binding.extensionDefinitionRevisionId(), Map.of("tags", List.of("A", "B"))));
        assertEquals(List.of("A", "B"), saved.fields().get("tags"));
        assertThrows(RuntimeException.class, () -> extensions.save(new EntityExtensionApi.Save(target, actor, 1L, 0,
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
        var binding = service.bind(new EntityFormApi.Bind(target, actor, 1L, 0, 10L, null, Map.of("power", "powerSupply")));
        assertNull(binding.extensionDefinitionRevisionId());
        verify(mapper, never()).insertDefinition(any());
    }

    @Test void optInBindingPersistsAdditionalCheckboxAndBusinessDetailRows() {
        revision.setFormRulesJson("[{\"type\":\"input\",\"field\":\"power\"},"
                + "{\"type\":\"checkbox\",\"field\":\"tags\",\"options\":[{\"value\":\"A\"}]},"
                + "{\"type\":\"group\",\"field\":\"devices\",\"props\":{\"rule\":[{\"type\":\"input\",\"field\":\"serial\"}]}},"
                + "{\"type\":\"PmsFileArtifact\",\"field\":\"ATTACHMENT\"}]");
        var binding = service.bind(new EntityFormApi.Bind(target, actor, 1L, 0, 10L, null,
                Map.of("power", "powerSupply"), true));
        assertEquals(Map.of("power", "powerSupply", "tags", "tags", "devices", "devices"), binding.fieldBindings());
        var values = Map.<String, Object>of("tags", List.of("A"), "devices", List.of(Map.of("serial", "SN001")));
        assertEquals(values, extensions.save(new EntityExtensionApi.Save(target, actor, 1L, 0,
                binding.extensionDefinitionRevisionId(), values)).fields());
        assertThrows(RuntimeException.class, () -> extensions.save(new EntityExtensionApi.Save(target, actor, 1L, 0,
                binding.extensionDefinitionRevisionId(), Map.of("devices", List.of("wrong row type")))));
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
    @Test void namedLayoutPreservesHiddenDefinitionsAndRejectsIncompatibleTypes(){
        var old=new EntityExtensionDefinitionDO();old.setId(90L);old.setTenantId(1L);old.setOwnerModule("SOL");old.setEntityType("SITE_SURVEY");old.setRevisionNo(1);
        old.setFieldsJson(JsonUtils.toJsonString(List.of(new EntityExtensionApi.Definition("hidden","Hidden",EntityField.Type.BOOLEAN,false,null,List.of()))));
        when(mapper.selectDefinition(90L)).thenReturn(old);
        var value=new cn.iocoder.yudao.module.pms.platform.dal.dataobject.entity.EntityExtensionValueDO();value.setDefinitionRevisionId(90L);value.setVersion(3);value.setValuesJson("{\"hidden\":false}");
        when(mapper.selectValues(any())).thenReturn(value);
        var result=service.bind(new EntityFormApi.Bind(target,actor,1L,0,10L,null,Map.of("power","powerSupply","tags","tags"),false,true));
        var combined=extensions.definition(result.extensionDefinitionRevisionId(),target.entity(),actor);
        assertEquals(Set.of("hidden","tags"),combined.fields().stream().map(EntityExtensionApi.Definition::code).collect(java.util.stream.Collectors.toSet()));
        assertEquals(EntityField.Type.BOOLEAN,combined.fields().stream().filter(field->field.code().equals("hidden")).findFirst().orElseThrow().type());
        assertEquals(false,extensions.read(target,actor).fields().get("hidden"));
        assertNull(mapper.selectDefinition(result.extensionDefinitionRevisionId()).getSourceFormRevisionId(),"Combined entity definitions are not the template's global immutable definition");
        assertEquals(1,extensions.definition(90L,target.entity(),actor).fields().size());
        assertThrows(RuntimeException.class,()->service.bind(new EntityFormApi.Bind(target,actor,1L,0,10L,null,Map.of("power","powerSupply","tags","hidden"),false,true)));
    }
    @Test void firstSaveCanRetainFieldsFromAnAuthorizedPreviouslySelectedLayout(){
        var compact=new DynamicFormTemplateRevisionDO();org.springframework.beans.BeanUtils.copyProperties(revision,compact);
        compact.setId(20L);compact.setFormRulesJson("[{\"type\":\"input\",\"field\":\"power\"}]");
        when(revisions.selectByRow(any())).thenAnswer(call->((cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery)call.getArgument(0)).revisionId()==20L?compact:revision);
        var command=new EntityFormApi.Bind(target,actor,1L,0,20L,null,Map.of("power","powerSupply"),false,true,
                List.of(new EntityFormApi.DefinitionSource(10L,Map.of("power","powerSupply","tags","tags"))));
        var binding=service.bind(command);
        assertEquals(20L,binding.formRevisionId());assertEquals(Map.of("power","powerSupply"),binding.fieldBindings());
        assertNotNull(binding.extensionDefinitionRevisionId());
        assertEquals(definitions,extensions.definition(binding.extensionDefinitionRevisionId(),target.entity(),actor).fields());
        assertEquals(List.of("A","B"),extensions.save(new EntityExtensionApi.Save(target,actor,1L,0,binding.extensionDefinitionRevisionId(),Map.of("tags",List.of("A","B")))).fields().get("tags"));
        assertNull(mapper.selectDefinition(binding.extensionDefinitionRevisionId()).getSourceFormRevisionId());
    }
    @Test void definitionSourcesCannotBypassPublicationTenantOrProviderAuthorization(){
        var compact=new DynamicFormTemplateRevisionDO();org.springframework.beans.BeanUtils.copyProperties(revision,compact);
        compact.setId(20L);compact.setFormRulesJson("[{\"type\":\"input\",\"field\":\"power\"}]");
        when(revisions.selectByRow(any())).thenAnswer(call->((cn.iocoder.yudao.module.pms.platform.dal.mysql.dynamicform.query.DynamicFormRevisionRowQuery)call.getArgument(0)).revisionId()==20L?compact:revision);
        var command=new EntityFormApi.Bind(target,actor,1L,0,20L,null,Map.of("power","powerSupply"),false,true,
                List.of(new EntityFormApi.DefinitionSource(10L,Map.of("tags","tags"))));
        revision.setStatusCode("DRAFT");assertThrows(RuntimeException.class,()->service.bind(command));
        revision.setStatusCode("PUBLISHED");revision.setTenantId(2L);assertThrows(RuntimeException.class,()->service.bind(command));
        revision.setTenantId(1L);
        doThrow(new IllegalStateException("source scope denied")).when(forms).inspectRevisionForUsage(argThat(query->query.templateRevisionId()==10L));
        assertThrows(IllegalStateException.class,()->service.bind(command));
        verify(mapper,never()).insertDefinition(any());verify(mapper,never()).insertBinding(any());
    }
}
