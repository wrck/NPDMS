package cn.iocoder.yudao.module.pms.engineering.api.requirement;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.*;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessAction;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.DynamicFormBusinessInstanceApi;
import cn.iocoder.yudao.module.pms.platform.api.dynamicform.dto.*;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectWorkBindingFactApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingFact;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectWorkBindingTarget;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequirementAnalysisFactApiImplTest {
    @Mock RequirementAnalysisRootMapper rootMapper;
    @Mock PermissionApi permissionApi;
    @Mock ProjectScopeApi projectScopeApi;
    @Mock ProjectOrganizationFactApi organizationFactApi;
    @Mock ProjectWorkBindingFactApi workBindingFactApi;
    @Mock DynamicFormBusinessInstanceApi dynamicFormApi;
    private RequirementAnalysisFactApiImpl api;

    @BeforeEach
    void setUp() {
        api = new RequirementAnalysisFactApiImpl(rootMapper, permissionApi, projectScopeApi,
                organizationFactApi, workBindingFactApi, dynamicFormApi);
        TenantContextHolder.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(9L).setTenantId(0L).setUserType(2),
                new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void inspectUsesOneReadOnlyTransactionSnapshot() throws Exception {
        Transactional annotation = RequirementAnalysisFactApiImpl.class
                .getMethod("inspect", RequirementAnalysisFactQuery.class).getAnnotation(Transactional.class);
        assertTrue(annotation.readOnly());
    }

    @Test
    void permissionFailsBeforeProjSolAndPlatformFacts() {
        when(permissionApi.hasAnyPermissions(9L, "pms:requirement-analysis:query")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> api.inspect(new RequirementAnalysisFactQuery(100L, 501L)));
        verifyNoInteractions(projectScopeApi, organizationFactApi, workBindingFactApi, rootMapper, dynamicFormApi);
    }

    @Test
    void inspectReturnsCompletedDynamicFormVectorAndHistoricalPointer() {
        stubProject();
        PreparationDO selected = root(501L, 1, null);
        PreparationDO effective = root(502L, 2, 1);
        when(rootMapper.selectById(any())).thenReturn(selected);
        when(rootMapper.selectEffective(any())).thenReturn(effective);
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form(selected));

        RequirementAnalysisFact fact = api.inspect(new RequirementAnalysisFactQuery(100L, 501L));

        assertFalse(fact.currentEffective());
        assertEquals(9001L, fact.dynamicFormInstanceId());
        assertEquals(4, fact.dynamicFormInstanceVersion());
        assertEquals(2, fact.orderedSectionFacts().size());
        assertEquals(fact.dynamicFormInstanceId(), fact.factVector().dynamicFormInstanceId());
        assertEquals(502L, fact.currentEffectivePreparationId());
        assertEquals(100L, fact.factVector().projectId());
        assertEquals(3, fact.factVector().projectVersion());
        assertEquals(201L, fact.factVector().workBindingFact().projectTaskId());
        assertEquals(16L, fact.factVector().dynamicFormTemplateId());
        assertEquals(17L, fact.factVector().dynamicFormTemplateRevisionId());
        assertEquals("FORM_CREATE_ELEMENT_PLUS", fact.factVector().engineCode());
        assertEquals(selected.getCompletedAt(), fact.factVector().completedAt());
        assertFalse(fact.factVector().currentEffective());
        assertEquals(502L, fact.factVector().currentEffectivePreparationId());
        InOrder order = inOrder(permissionApi, projectScopeApi, organizationFactApi, workBindingFactApi,
                rootMapper, dynamicFormApi);
        order.verify(permissionApi).hasAnyPermissions(9L, "pms:requirement-analysis:query");
        order.verify(projectScopeApi).resolveCurrent(any());
        order.verify(organizationFactApi).inspect(any());
        order.verify(rootMapper).selectById(any());
        order.verify(workBindingFactApi).inspectTask(argThat(query -> query.projectTaskId().equals(201L)));
        order.verify(rootMapper).selectEffective(any());
        order.verify(dynamicFormApi).inspectEntityData(any());
    }

    @Test
    void inspectKeepsHistoricalCompletedVersionReadableAfterCurrentBindingRevisionChanges() {
        stubProject();
        PreparationDO historical = root(501L, 1, null);
        PreparationDO effective = root(502L, 2, 1);
        ProjectWorkBindingFact currentBinding = binding(18L, 2, 3);
        when(workBindingFactApi.inspectTask(any())).thenReturn(currentBinding);
        when(rootMapper.selectById(any())).thenReturn(historical);
        when(rootMapper.selectEffective(any())).thenReturn(effective);
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form(historical));

        RequirementAnalysisFact fact = api.inspect(new RequirementAnalysisFactQuery(100L, 501L));

        assertFalse(fact.currentEffective());
        assertEquals(17L, fact.factVector().dynamicFormTemplateRevisionId());
        assertEquals(18L, currentBinding.dynamicFormTemplateRevisionId());
        assertEquals(18L, fact.factVector().workBindingFact().dynamicFormTemplateRevisionId());
        assertEquals(702L, fact.factVector().workBindingFact().projectTemplateRevisionId());
    }

    @Test
    void lockRevalidatesProjThenSolThenPlatformAndComparesWholeVector() {
        stubProject();
        PreparationDO selected = root(501L, 1, 1);
        when(rootMapper.selectById(any())).thenReturn(selected);
        when(rootMapper.selectEffective(any())).thenReturn(selected);
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form(selected));
        RequirementAnalysisFact inspected = api.inspect(new RequirementAnalysisFactQuery(100L, 501L));
        clearInvocations(projectScopeApi, organizationFactApi, workBindingFactApi, rootMapper, dynamicFormApi);

        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(organizationFactApi.lockAndRevalidate(any())).thenReturn(project());
        when(workBindingFactApi.lockAndRevalidate(any())).thenReturn(binding());
        when(rootMapper.selectForUpdate(any())).thenReturn(selected);
        when(rootMapper.selectEffectiveForUpdate(any())).thenReturn(selected);
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form(selected));
        when(dynamicFormApi.lockAndRevalidateInstance(any())).thenReturn(form(selected));

        RequirementAnalysisFact locked = api.lockAndRevalidate(new RequirementAnalysisFactRevalidationQuery(
                100L, 501L, 1, 5, 3, 702L, inspected.factVector()));

        assertEquals(inspected.factVector(), locked.factVector());
        InOrder order = inOrder(projectScopeApi, organizationFactApi, workBindingFactApi, rootMapper, dynamicFormApi);
        order.verify(projectScopeApi).resolveCurrent(any());
        order.verify(projectScopeApi).lockAndRevalidate(any());
        order.verify(organizationFactApi).lockAndRevalidate(any());
        order.verify(workBindingFactApi).lockAndRevalidate(any());
        order.verify(rootMapper).selectForUpdate(any());
        order.verify(rootMapper).selectEffectiveForUpdate(any());
        order.verify(dynamicFormApi).inspectEntityData(any());
        order.verify(dynamicFormApi).lockAndRevalidateInstance(any());
    }

    @Test
    void lockRejectsWhenCurrentEffectiveIdentityChanged() {
        stubProject();
        PreparationDO selected = root(501L, 1, 1);
        when(rootMapper.selectById(any())).thenReturn(selected);
        when(rootMapper.selectEffective(any())).thenReturn(selected);
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form(selected));
        RequirementAnalysisFact inspected = api.inspect(new RequirementAnalysisFactQuery(100L, 501L));

        PreparationDO newerEffective = root(502L, 2, 1);
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(organizationFactApi.lockAndRevalidate(any())).thenReturn(project());
        when(workBindingFactApi.lockAndRevalidate(any())).thenReturn(binding());
        when(rootMapper.selectForUpdate(any())).thenReturn(selected);
        when(rootMapper.selectEffectiveForUpdate(any())).thenReturn(newerEffective);
        when(dynamicFormApi.lockAndRevalidateInstance(any())).thenReturn(form(selected));

        assertThrows(RuntimeException.class, () -> api.lockAndRevalidate(
                new RequirementAnalysisFactRevalidationQuery(100L, 501L, 1, 5, 3, 702L,
                        inspected.factVector())));
        verify(dynamicFormApi).lockAndRevalidateInstance(any());
    }

    @Test
    void stageOriginFactUsesExplicitStageAndRevalidatesItsVersionWithoutATask() {
        when(permissionApi.hasAnyPermissions(9L, "pms:requirement-analysis:query")).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(organizationFactApi.inspect(any())).thenReturn(project());
        var selected = root(501L, 1, 1);
        freezeOrigin(selected, stageBinding(4));
        String frozen = selected.getTemplateSnapshot();
        when(rootMapper.selectById(any())).thenReturn(selected);
        when(rootMapper.selectEffective(any())).thenReturn(selected);
        when(workBindingFactApi.inspectStage(any())).thenReturn(stageBinding(4));
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form(selected));
        var inspected = api.inspect(new RequirementAnalysisFactQuery(100L, 501L));
        assertNull(inspected.workBindingFact().projectTaskId());
        assertEquals(202L, inspected.workBindingFact().projectStageId());
        assertEquals(4, inspected.workBindingFact().projectStageVersion());
        verify(workBindingFactApi).inspectStage(argThat(query -> query.projectStageId().equals(202L)));
        verify(workBindingFactApi, never()).inspect(any());
        verify(workBindingFactApi, never()).inspectTask(any());

        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(organizationFactApi.lockAndRevalidate(any())).thenReturn(project());
        when(workBindingFactApi.lockAndRevalidateStage(any())).thenReturn(stageBinding(4));
        when(rootMapper.selectForUpdate(any())).thenReturn(selected);
        when(rootMapper.selectEffectiveForUpdate(any())).thenReturn(selected);
        when(dynamicFormApi.lockAndRevalidateInstance(any())).thenReturn(form(selected));
        var query = new RequirementAnalysisFactRevalidationQuery(100L, 501L, 1, 5, 3, 702L, inspected.factVector());
        assertEquals(inspected.factVector(), api.lockAndRevalidate(query).factVector());
        verify(workBindingFactApi).lockAndRevalidateStage(argThat(expected -> expected.projectStageId().equals(202L)
                && expected.expectedProjectStageVersion() == 4 && expected.executionContractId().equals(301L)));
        verify(workBindingFactApi, never()).lockAndRevalidate(any());
        assertEquals(frozen, selected.getTemplateSnapshot());

        when(workBindingFactApi.lockAndRevalidateStage(any())).thenReturn(stageBinding(5));
        assertThrows(RuntimeException.class, () -> api.lockAndRevalidate(query));
    }

    @Test
    void taskOriginNeverUsesAmbiguousProjectWideDefaultBinding() {
        stubProject();
        var selected = root(501L, 1, 1);
        when(rootMapper.selectById(any())).thenReturn(selected);
        when(rootMapper.selectEffective(any())).thenReturn(selected);
        when(dynamicFormApi.inspectEntityData(any())).thenReturn(form(selected));
        assertEquals(201L, api.inspect(new RequirementAnalysisFactQuery(100L, 501L)).workBindingFact().projectTaskId());
        verify(workBindingFactApi, never()).inspect(any());
        verify(workBindingFactApi, never()).inspectStage(any());
    }

    @Test
    void missingOriginDoesNotFallBackToTheProjectDefault() {
        when(permissionApi.hasAnyPermissions(9L, "pms:requirement-analysis:query")).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(organizationFactApi.inspect(any())).thenReturn(project());
        var selected = root(501L, 1, 1); selected.setTemplateSnapshot(null);
        when(rootMapper.selectById(any())).thenReturn(selected);
        assertThrows(RuntimeException.class, () -> api.inspect(new RequirementAnalysisFactQuery(100L, 501L)));
        verifyNoInteractions(workBindingFactApi, dynamicFormApi);
    }

    @Test
    void returnedBindingForAnotherNodeCannotProveTheSelectedBusinessRecord() {
        stubProject();
        var selected = root(501L, 1, 1);
        when(rootMapper.selectById(any())).thenReturn(selected);
        when(workBindingFactApi.inspectTask(any())).thenReturn(stageBinding(4));
        assertThrows(RuntimeException.class, () -> api.inspect(new RequirementAnalysisFactQuery(100L, 501L)));
        verifyNoInteractions(dynamicFormApi);
    }

    private void stubProject() {
        when(permissionApi.hasAnyPermissions(9L, "pms:requirement-analysis:query")).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(organizationFactApi.inspect(any())).thenReturn(project());
        when(workBindingFactApi.inspectTask(any())).thenReturn(binding());
    }

    private ProjectScopeResult scope() {
        return new ProjectScopeResult(100L, 8L, Set.of(100L), Set.of());
    }

    private ProjectOrganizationFact project() {
        return new ProjectOrganizationFact(100L, 3, 10L, 20L, "D20");
    }

    private ProjectWorkBindingFact binding() {
        return binding(17L, 1, 2);
    }

    private ProjectWorkBindingFact binding(Long dynamicRevisionId, Integer dynamicRevisionNo,
                                           Integer dynamicRevisionFactVersion) {
        ProjectWorkBindingTarget target = ProjectWorkBindingTarget.REQUIREMENT_ANALYSIS;
        return new ProjectWorkBindingFact(100L, 3, 201L, 4, 301L, 5,
                401L, 6, target.workBindingTypeCode(), target.targetContextCode(),
                target.targetObjectType(), target.targetObjectKey(), null, null, null,
                null, 702L, 2, "{}", 16L, dynamicRevisionId, dynamicRevisionNo,
                dynamicRevisionFactVersion);
    }

    private PreparationDO root(long id, int businessVersion, Integer effective) {
        PreparationDO row = new PreparationDO();
        row.setId(id);
        row.setTenantId(0L);
        row.setProjectId(100L);
        row.setBusinessVersion(businessVersion);
        row.setStatusCode("COMPLETED");
        row.setEffectiveMarker(effective);
        row.setContentVersion(5);
        row.setTemplateId(401L);
        row.setTemplateRevisionId(702L);
        row.setDynamicFormInstanceId(9001L);
        row.setEntityValueJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(form(row).ordinaryValues()));
        row.setCompletedBy(9L);
        row.setCompletedAt(LocalDateTime.now());
        freezeOrigin(row, binding());
        return row;
    }

    private void freezeOrigin(PreparationDO row, ProjectWorkBindingFact binding) {
        var task = binding.projectTaskId() == null ? null : new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                100L, 3, binding.projectTaskId(), binding.projectTaskVersion(), 301L, 5, 702L,
                801L, 1, 1, 802L, 1, true, LocalDateTime.now());
        var stage = binding.projectStageId() == null ? null : new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext(
                100L, 3, binding.projectStageId(), binding.projectStageVersion(), 301L, 5, 702L, 802L, 1, 1, true);
        row.setTemplateSnapshot(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                new cn.iocoder.yudao.module.pms.engineering.service.requirement.RequirementAnalysisExecutionBinding.Frozen(binding, task, stage)));
    }

    private ProjectWorkBindingFact stageBinding(int version) {
        var task = binding();
        return new ProjectWorkBindingFact(task.projectId(), task.projectVersion(), null, null,
                task.executionContractId(), task.contractVersion(), task.projectTemplateId(), null,
                task.workBindingTypeCode(), task.targetContextCode(), task.targetObjectType(), task.targetObjectKey(),
                null, null, null, null, task.templateRevisionId(), task.templateRevisionNo(), task.bindingParameterSnapshot(),
                task.dynamicFormTemplateId(), task.dynamicFormTemplateRevisionId(), task.dynamicFormRevisionNo(),
                task.dynamicFormRevisionFactVersion(), 202L, version);
    }

    private DynamicFormInstanceFact form(PreparationDO root) {
        DynamicFormProviderKey provider = new DynamicFormProviderKey("SOL", "REQUIREMENT_ANALYSIS");
        return new DynamicFormInstanceFact(0L, provider,
                new DynamicFormOwnerKey("SOL", "REQUIREMENT_ANALYSIS", String.valueOf(root.getId())),
                9001L, 16L, 17L, 1, 2, "FORM_CREATE_ELEMENT_PLUS", "3.4.0", "3.2.38",
                "{}", "[]", List.of(
                new DynamicFormFieldDescriptor("PROJECT_BACKGROUND", "Editor", false, true,
                        "STRING", null, null, null, List.of()),
                new DynamicFormFieldDescriptor("PROJECT_BACKGROUND__ATTACHMENTS", "PmsFileArtifact", true,
                        false, "FILES", null, null, null, List.of())),
                Map.of("PROJECT_BACKGROUND", "<p>背景</p>"),
                new DynamicFormValidationFact("VALID", List.of()), List.of(), 4,
                DynamicFormBusinessAction.READ,
                new DynamicFormPolicyFact(DynamicFormBusinessAction.READ, true, null, 1L, "COMPLETED"));
    }
}
