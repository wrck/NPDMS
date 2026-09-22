package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.DynamicFormFieldDescriptor;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequirementAnalysisPresentationsTest {
    final RequirementAnalysisAccess access = mock(RequirementAnalysisAccess.class);
    final EntityPresentationApi catalog = mock(EntityPresentationApi.class);
    final EntityExtensionApi extensions = mock(EntityExtensionApi.class);
    final ConfigApi configs = mock(ConfigApi.class);
    final RequirementAnalysisPresentations service = new RequirementAnalysisPresentations(access,catalog,extensions,configs,mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi.class));
    final EntityActor actor = new EntityActor(1L,7L,null);

    void setup() {
        var row = new RequirementAnalysisRevisionDO(); row.setId(9L); row.setEntityId(8L); row.setProjectId(5L); row.setTenantId(1L);
        row.setRevisionState("FROZEN");
        when(access.read(9L,actor)).thenReturn(row);
        when(extensions.read(any(),any())).thenReturn(new EntityExtensionApi.Values(null,Map.of(),0));
    }

    @Test void historicalReadCanChoosePartialLayoutWithoutBindingOrChangingData() {
        setup();
        when(catalog.list(any())).thenReturn(List.of(presentation("PROJECT_BACKGROUND")));
        when(configs.getConfigValueByKey(any())).thenReturn("invalid");
        var result = service.list(9L,actor);
        assertNull(result.defaultTemplateId());
        assertEquals("projectBackground",result.options().getFirst().layout().binding().fieldBindings().get("PROJECT_BACKGROUND"));
        verify(access).read(9L,actor);
        verifyNoMoreInteractions(access);
    }

    @Test void layoutsCannotInventBusinessFieldsOrDuplicateAliases() {
        setup();
        when(catalog.list(any())).thenReturn(List.of(presentation("UNREGISTERED"),presentation("PROJECT_BACKGROUND","projectBackground")));
        assertTrue(service.list(9L,actor).options().isEmpty());
    }

    @Test void revokedBusinessReadCannotExposeTemplateOrData() {
        doThrow(new IllegalStateException("FORBIDDEN")).when(access).read(9L,actor);
        assertThrows(RuntimeException.class,()->service.list(9L,actor));
        verifyNoInteractions(catalog,extensions,configs);
    }

    private EntityPresentationApi.Presentation presentation(String... fields) {
        return new EntityPresentationApi.Presentation(30L,"Layout",40L,1,1,"FORM_CREATE_ELEMENT_PLUS","3","3","{}","[]",
                Arrays.stream(fields).map(field -> new DynamicFormFieldDescriptor(field,"Editor",false,false,"string",null,null,null,List.of())).toList());
    }
}
