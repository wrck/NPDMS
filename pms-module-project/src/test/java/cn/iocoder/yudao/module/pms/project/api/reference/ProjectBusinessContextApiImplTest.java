package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessContextApiImplTest {
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    private final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    private final ProjectBusinessContextApiImpl service = new ProjectBusinessContextApiImpl(projects, scopes, members);
    private final ProjectBusinessContextApi.Query query = ProjectBusinessContextApi.Query.builder()
            .tenantId(7L).actorId(13L).projectId(29L).expectedScopeVersion(41L).build();
    private final ProjectMasterDO project = new ProjectMasterDO();

    @BeforeEach
    void setup() {
        TenantContextHolder.setTenantId(7L);
        project.setId(29L);
        project.setTenantId(7L);
        project.setVersion(53L);
        project.setProjectCode("P-29");
        project.setProjectName("项目29");
        project.setCustomerId(67L);
        project.setCustomerCode("C-67");
        project.setCustomerName("客户67");
        project.setDepartmentId(79L);
        project.setDepartmentCode("D-79");
        project.setDepartmentName("部门79");
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void optionalHardwareIsExcludedOnlyByExplicitFrozenPolicy() {
        var plans = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "plans", plans);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(29L), Set.of()));
        when(projects.selectById(29L)).thenReturn(project); project.setActivePlanVersionId(101L);
        var snapshot = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot();
        var task = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.TaskContract();
        task.setCode("HARDWARE"); task.setRequired(false);
        var binding = new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot.BindingContract();
        binding.setType("BUSINESS_OBJECT"); binding.setTargetContextCode("IMP"); binding.setTargetObjectType("INSTALLATION"); task.setBinding(binding);
        snapshot.setTasks(java.util.List.of(task));
        snapshot.setImplementationReadiness(new cn.iocoder.yudao.module.pms.project.domain.template.TemplateImplementationReadiness(java.util.Map.of("EXE_02", "HARDWARE")));
        var plan = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO();
        plan.setId(101L); plan.setVersion(3L);
        plan.setExecutionSnapshot(cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader.canonicalJson(snapshot));
        when(plans.selectEffective(any())).thenReturn(plan);
        var policy = service.implementationRequirements(query);
        assertTrue(policy.configured()); assertEquals(101L, policy.planVersionId()); assertTrue(policy.requiredSourceCodes().isEmpty());
    }

    @Test
    void legacyFrozenPlanWithoutPolicyKeepsAllFourOwnerRequirements() {
        var plans = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "plans", plans);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(29L), Set.of()));
        when(projects.selectById(29L)).thenReturn(project); project.setActivePlanVersionId(101L);
        var plan = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO();
        plan.setId(101L); plan.setVersion(3L);
        plan.setExecutionSnapshot(cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshotReader.canonicalJson(
                new cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot()));
        when(plans.selectEffective(any())).thenReturn(plan);
        var policy = service.implementationRequirements(query);
        assertFalse(policy.configured()); assertEquals(Set.of("EXE_01", "EXE_02", "EXE_03", "EXE_04"), policy.requiredSourceCodes());
    }

    @Test
    void forbiddenScopeCannotReadFrozenImplementationPlan() {
        var plans = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "plans", plans);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(), Set.of(29L)));
        assertThrows(ServiceException.class, () -> service.implementationRequirements(query));
        verifyNoInteractions(projects, plans);
    }

    @Test
    void authorizedSnapshotKeepsProjectCustomerDepartmentAndVersionsDistinct() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(29L), Set.of()));
        when(projects.selectById(29L)).thenReturn(project);

        assertEquals(ProjectBusinessContextApi.Context.builder()
                .tenantId(7L).projectId(29L).projectVersion(53L).scopeVersion(41L)
                .projectCode("P-29").projectName("项目29")
                .customerId(67L).customerCode("C-67").customerName("客户67")
                .departmentId(79L).departmentCode("D-79").departmentName("部门79").build(),
                service.inspect(query));
        verify(scopes).resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(7L).subjectUserId(13L).anchorProjectId(29L).actionCode(ProjectScopeApi.ACTION_VIEW).build());
    }

    @Test
    void tenantMismatchCannotReadScopeOrProject() {
        TenantContextHolder.setTenantId(8L);
        assertEquals(PROJECT_TREE_SCOPE_FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> service.inspect(query)).getCode());
        verifyNoInteractions(scopes, projects, members);
    }

    @Test
    void presentationUsesAuthorizedProjectAndCurrentMemberNamesWithoutChangingFrozenContext() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(29L), Set.of()));
        when(projects.selectById(29L)).thenReturn(project);
        project.setManagerName("项目经理"); project.setIndustryName("金融"); project.setMajorProjectLevel("公司级");
        var manager = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO();
        manager.setMemberRole("SERVICE_MANAGER"); manager.setMemberName("服务经理");
        var sales = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO();
        sales.setMemberRole("SALES_REPRESENTATIVE"); sales.setMemberName("销售");
        when(members.selectActiveForAssignmentState(any())).thenReturn(java.util.List.of(manager, manager, sales));
        var frozen = service.inspect(query);
        var result = service.presentation(query);
        assertEquals("项目经理", result.projectManagerName());
        assertEquals("服务经理", result.serviceManagerNames());
        assertEquals("销售", result.salesNames());
        assertEquals("金融", result.industryName());
        assertEquals("客户67", result.customerName());
        assertEquals(frozen, service.inspect(query));
        verify(members).selectActiveForAssignmentState(argThat(value -> value.projectId().equals(29L) && value.effectiveAt() != null));
    }

    @Test
    void presentationCannotReadMembersOutsideScope() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(), Set.of(29L)));
        assertThrows(ServiceException.class, () -> service.presentation(query));
        verifyNoInteractions(projects, members);
    }

    @Test
    void missingPermissionAndStaleScopeCannotReadProject() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(), Set.of(29L)));
        assertEquals(PROJECT_TREE_SCOPE_FORBIDDEN.getCode(), assertThrows(ServiceException.class,
                () -> service.inspect(query)).getCode());
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(29L, 42L, Set.of(29L), Set.of()));
        assertEquals(PROJECT_TREE_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.inspect(query)).getCode());
        verifyNoInteractions(projects, members);
    }

    @Test
    void revalidationLocksScopeBeforeProjectAndRejectsProjectVersionChange() {
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(29L, 41L, Set.of(29L), Set.of()));
        when(projects.selectByIdForUpdate(29L)).thenReturn(project);
        var expected = ProjectBusinessContextApi.Revalidation.builder().query(query).expectedProjectVersion(52L).build();

        assertEquals(PROJECT_VERSION_CONFLICT.getCode(), assertThrows(ServiceException.class,
                () -> service.lockAndRevalidate(expected)).getCode());
        var order = inOrder(scopes, projects);
        order.verify(scopes).lockAndRevalidate(argThat(value -> value.tenantId().equals(7L)
                && value.subjectUserId().equals(13L) && value.anchorProjectId().equals(29L)
                && value.expectedScopeVersion().equals(41L) && value.actionCode().equals(ProjectScopeApi.ACTION_VIEW)));
        order.verify(projects).selectByIdForUpdate(29L);
        verify(projects, never()).updateById(any(ProjectMasterDO.class));
    }
}
