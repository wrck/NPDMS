package cn.iocoder.yudao.module.pms.project.api.workbinding;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttemplate.ProjectTemplateRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectTemplateService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessConfigurationApiImplTest {
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectTemplateService templates = mock(ProjectTemplateService.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectBusinessConfigurationApiImpl api = new ProjectBusinessConfigurationApiImpl(projects, templates, scopes);
    private final ProjectMasterDO project = new ProjectMasterDO();
    private final ProjectTemplateRevisionDO publication = new ProjectTemplateRevisionDO();
    private final TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();
    private final ProjectBusinessConfigurationApi.Query query = new ProjectBusinessConfigurationApi.Query(
            7L, 19L, 80L, ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS);

    @BeforeEach void setUp() {
        TenantContextHolder.setTenantId(7L);
        project.setId(80L); project.setTenantId(7L); project.setLifecycleTemplateId(90L);
        project.setLifecycleTemplateRevisionId(91L); project.setLifecycleTemplateRevisionNo(3);
        project.setActivePlanVersionId(999L); project.setCurrentStage("S6");
        publication.setId(91L); publication.setTenantId(7L); publication.setTemplateId(90L);
        publication.setRevisionNo(3); publication.setStatus("PUBLISHED");
        var stage = new TemplateExecutionSnapshot.StageContract(); stage.setBinding(binding()); snapshot.getStages().add(stage);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(80L, 2L, Set.of(80L), Set.of()));
        when(projects.selectById(80L)).thenReturn(project);
        when(templates.getRevisionById(91L)).thenReturn(publication);
        when(templates.getExecutionSnapshot(90L, 3)).thenReturn(snapshot);
    }
    @AfterEach void clearTenant() { TenantContextHolder.clear(); }

    @Test void returnsExactInitialPublicationRegardlessOfCurrentStageAndPlan() {
        String before = JsonUtils.toJsonString(snapshot);
        var found = api.resolve(query);
        assertEquals(91L, found.templateRevisionId()); assertEquals(3, found.templateRevisionNo());
        assertEquals(101L, JsonUtils.parseTree(found.parameters()).get("dynamicFormTemplateRevisionId").longValue());
        assertEquals(before, JsonUtils.toJsonString(snapshot));
        verify(templates).getRevisionById(91L); verify(templates).getExecutionSnapshot(90L, 3);
        verifyNoMoreInteractions(templates);
        verify(projects, never()).selectByIdForUpdate(any());
    }
    @Test void identicalOwnerConfigurationsAcrossStageAndTaskAreNotAmbiguous() {
        var task = new TemplateExecutionSnapshot.TaskContract(); var binding = binding();
        binding.setBusinessViewSnapshot(JsonUtils.parseTree("{\"name\":\"another view\"}"));
        binding.setOperationContract(JsonUtils.parseTree("{\"different\":true}"));
        task.setBinding(binding); snapshot.getTasks().add(task);
        assertDoesNotThrow(() -> api.resolve(query));
    }
    @ParameterizedTest @ValueSource(strings = {"parameters", "form"})
    void rejectsConflictingOwnerConfigurationsInsteadOfSelectingTheFirst(String damage) {
        var task = new TemplateExecutionSnapshot.TaskContract(); var binding = binding();
        if (damage.equals("parameters")) binding.setParameters(JsonUtils.parseTree("{\"dynamicFormTemplateRevisionId\":102}"));
        else binding.setDynamicFormRevisionId(102L);
        task.setBinding(binding); snapshot.getTasks().add(task);
        assertEquals("BUSINESS_CONFIGURATION_AMBIGUOUS", assertThrows(IllegalStateException.class, () -> api.resolve(query)).getMessage());
    }
    @ParameterizedTest @ValueSource(strings = {"owner", "type", "key", "binding", "empty"})
    void neverBorrowsConfigurationFromAnotherOwnerOrTarget(String damage) {
        var binding = snapshot.getStages().getFirst().getBinding();
        switch (damage) {
            case "owner" -> binding.setTargetContextCode("OTHER");
            case "type" -> binding.setTargetObjectType("OTHER");
            case "key" -> binding.setTargetObjectKey("OTHER");
            case "binding" -> binding.setType("BUSINESS_COMPONENT");
            case "empty" -> snapshot.getStages().clear();
            default -> throw new AssertionError(damage);
        }
        assertEquals("BUSINESS_CONFIGURATION_UNAVAILABLE", assertThrows(IllegalStateException.class, () -> api.resolve(query)).getMessage());
    }
    @ParameterizedTest @ValueSource(strings = {"project-tenant", "publication-tenant", "template", "revision", "number", "draft", "missing"})
    void refusesBrokenPublicationIdentityWithoutResolvingCurrentOrLatest(String damage) {
        switch (damage) {
            case "project-tenant" -> project.setTenantId(8L);
            case "publication-tenant" -> publication.setTenantId(8L);
            case "template" -> publication.setTemplateId(92L);
            case "revision" -> publication.setId(92L);
            case "number" -> publication.setRevisionNo(4);
            case "draft" -> publication.setStatus("DRAFT");
            case "missing" -> when(templates.getRevisionById(91L)).thenReturn(null);
            default -> throw new AssertionError(damage);
        }
        assertThrows(RuntimeException.class, () -> api.resolve(query));
        verify(templates, never()).getExecutionSnapshot(any(), any());
    }
    @Test void scopeAndTenantFailuresStopBeforeLoadingConfiguration() {
        assertThrows(RuntimeException.class, () -> api.resolve(new ProjectBusinessConfigurationApi.Query(8L,19L,80L,query.target())));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(80L,2L,Set.of(),Set.of(80L)));
        assertThrows(RuntimeException.class, () -> api.resolve(query));
        verifyNoInteractions(projects, templates);
    }
    private TemplateExecutionSnapshot.BindingContract binding() {
        var result = new TemplateExecutionSnapshot.BindingContract();
        result.setType("BUSINESS_OBJECT"); result.setTargetContextCode("SOL");
        result.setTargetObjectType("REQUIREMENT_ANALYSIS"); result.setTargetObjectKey("PRE_04_REQUIREMENT_ANALYSIS");
        result.setParameters(JsonUtils.parseTree("{\"dynamicFormTemplateRevisionId\":101}"));
        return result;
    }
}
