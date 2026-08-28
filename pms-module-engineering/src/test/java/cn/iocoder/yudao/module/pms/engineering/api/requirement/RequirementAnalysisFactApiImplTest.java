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
        when(dynamicFormApi.inspectInstance(any())).thenReturn(form(selected));

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
        order.verify(workBindingFactApi).inspect(any());
        order.verify(rootMapper).selectById(any());
        order.verify(rootMapper).selectEffective(any());
        order.verify(dynamicFormApi).inspectInstance(any());
    }

    @Test
    void inspectKeepsHistoricalCompletedVersionReadableAfterCurrentBindingRevisionChanges() {
        stubProject();
        PreparationDO historical = root(501L, 1, null);
        PreparationDO effective = root(502L, 2, 1);
        ProjectWorkBindingFact currentBinding = binding(18L, 2, 3);
        when(workBindingFactApi.inspect(any())).thenReturn(currentBinding);
        when(rootMapper.selectById(any())).thenReturn(historical);
        when(rootMapper.selectEffective(any())).thenReturn(effective);
        when(dynamicFormApi.inspectInstance(any())).thenReturn(form(historical));

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
        when(dynamicFormApi.inspectInstance(any())).thenReturn(form(selected));
        RequirementAnalysisFact inspected = api.inspect(new RequirementAnalysisFactQuery(100L, 501L));
        clearInvocations(projectScopeApi, organizationFactApi, workBindingFactApi, rootMapper, dynamicFormApi);

        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(projectScopeApi.lockAndRevalidate(any())).thenReturn(scope());
        when(organizationFactApi.lockAndRevalidate(any())).thenReturn(project());
        when(workBindingFactApi.lockAndRevalidate(any())).thenReturn(binding());
        when(rootMapper.selectForUpdate(any())).thenReturn(selected);
        when(rootMapper.selectEffectiveForUpdate(any())).thenReturn(selected);
        when(dynamicFormApi.inspectInstance(any())).thenReturn(form(selected));
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
        order.verify(dynamicFormApi).inspectInstance(any());
        order.verify(dynamicFormApi).lockAndRevalidateInstance(any());
    }

    @Test
    void lockRejectsWhenCurrentEffectiveIdentityChanged() {
        stubProject();
        PreparationDO selected = root(501L, 1, 1);
        when(rootMapper.selectById(any())).thenReturn(selected);
        when(rootMapper.selectEffective(any())).thenReturn(selected);
        when(dynamicFormApi.inspectInstance(any())).thenReturn(form(selected));
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

    private void stubProject() {
        when(permissionApi.hasAnyPermissions(9L, "pms:requirement-analysis:query")).thenReturn(true);
        when(projectScopeApi.resolveCurrent(any())).thenReturn(scope());
        when(organizationFactApi.inspect(any())).thenReturn(project());
        when(workBindingFactApi.inspect(any())).thenReturn(binding());
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
        row.setCompletedBy(9L);
        row.setCompletedAt(LocalDateTime.now());
        return row;
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
