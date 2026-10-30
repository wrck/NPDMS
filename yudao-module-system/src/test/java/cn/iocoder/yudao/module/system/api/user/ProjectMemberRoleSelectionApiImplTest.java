package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionAnnotationAdvisor;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.ProjectMemberRoleSelectionMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.ProjectMemberRoleUserPageQuery;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectMemberRoleSelectionApiImplTest {
    ProjectMemberRoleSelectionMapper users;
    DeptMapper departments;
    PermissionApi permissions;
    ProjectMemberRoleSelectionApiImpl api;
    static final Set<String> MANAGEMENT = Set.of("PROJECT_MANAGER", "SERVICE_MANAGER", "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");

    @BeforeEach
    void setup() {
        users = mock(ProjectMemberRoleSelectionMapper.class);
        departments = mock(DeptMapper.class);
        permissions = mock(PermissionApi.class);
        api = new ProjectMemberRoleSelectionApiImpl(users, departments, permissions);
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
        when(permissions.hasAnyPermissions(7L, "pms:project:query")).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        DataPermissionContextHolder.clear();
    }

    ProjectMemberRoleSelectionApi.Query query(String keyword) {
        return new ProjectMemberRoleSelectionApi.Query(2, 1, keyword, MANAGEMENT);
    }

    @Test
    void queriesAllTrustedRoleCandidatesWithoutRequiringProjectMembershipOrCompanyQualification() {
        when(users.selectCount(any())).thenReturn(2L);
        var person = new AdminUserDO().setId(99L).setUsername("other-region").setNickname("范围外项目人员")
                .setEmail("never-returned@test.invalid").setMobile("never-returned");
        when(users.selectPage(any())).thenReturn(List.of(person));
        var result = api.page(query(" zhangsan "));
        assertEquals(2L, result.getTotal());
        assertEquals(new ActiveUserSelectionApi.MemberOption(99L, "other-region", "范围外项目人员", null, null), result.getList().getFirst());
        var captured = ArgumentCaptor.forClass(ProjectMemberRoleUserPageQuery.class);
        verify(users).selectPage(captured.capture());
        assertEquals(MANAGEMENT, captured.getValue().roleCodes());
        assertEquals("zhangsan", captured.getValue().keyword());
        assertEquals(1L, captured.getValue().tenantId());
        assertEquals(1L, captured.getValue().offset());
        assertNull(DataPermissionContextHolder.get());
    }

    @Test
    void missingQueryPermissionForeignTenantAndMissingIdentityNeverReadRoleDirectory() {
        when(permissions.hasAnyPermissions(7L, "pms:project:query")).thenReturn(false);
        assertThrows(RuntimeException.class, () -> api.page(query(null)));
        when(permissions.hasAnyPermissions(7L, "pms:project:query")).thenReturn(true);
        TenantContextHolder.setTenantId(2L);
        assertThrows(RuntimeException.class, () -> api.page(query(null)));
        TenantContextHolder.setTenantId(1L);
        SecurityContextHolder.clearContext();
        assertThrows(RuntimeException.class, () -> api.page(query(null)));
        verifyNoInteractions(users, departments);
    }

    @Test
    void invalidRolesAndPagingAreRejectedAndEmptyRolesNeverMeanAllUsers() {
        for (var invalid : List.of(
                new ProjectMemberRoleSelectionApi.Query(0, 20, null, MANAGEMENT),
                new ProjectMemberRoleSelectionApi.Query(1, 101, null, MANAGEMENT),
                new ProjectMemberRoleSelectionApi.Query(1, 20, "x".repeat(65), MANAGEMENT),
                new ProjectMemberRoleSelectionApi.Query(1, 20, null, null),
                new ProjectMemberRoleSelectionApi.Query(1, 20, null, Set.of("ADMIN")))) {
            assertThrows(RuntimeException.class, () -> api.page(invalid));
        }
        assertTrue(api.page(new ProjectMemberRoleSelectionApi.Query(1, 20, null, Set.of())).getList().isEmpty());
        verifyNoInteractions(users, departments);
    }

    @Test
    void zeroKeywordSkipsDepartmentSearchAndEmptyCountSkipsPageAndNameQueries() {
        when(users.selectCount(any())).thenReturn(0L);
        assertTrue(api.page(query(null)).getList().isEmpty());
        verify(users, never()).selectDepartmentIds(any());
        verify(users, never()).selectPage(any());
        verifyNoInteractions(departments);
    }

    @Test
    void ordinaryDepartmentPermissionIsRestoredInsideAnIgnoringCallerAndOnlyRoleDepartmentsAreSearched() {
        var factory = new ProxyFactory(api);
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new DataPermissionAnnotationAdvisor());
        var proxy = (ProjectMemberRoleSelectionApi) factory.getProxy();
        when(users.selectDepartmentIds(any())).thenAnswer(call -> {
            assertFalse(DataPermissionContextHolder.get().enable());
            return Set.of(10L, 20L);
        });
        when(departments.selectProjectMemberDepartments(any())).thenAnswer(call -> {
            assertTrue(DataPermissionContextHolder.get().enable());
            var request = (cn.iocoder.yudao.module.system.dal.mysql.dept.query.ProjectMemberDepartmentsQuery) call.getArgument(0);
            assertEquals(Set.of(10L, 20L), request.departmentIds());
            return List.of(new DeptDO().setId(10L).setName("可见部门"));
        });
        when(users.selectCount(any())).thenAnswer(call -> {
            assertFalse(DataPermissionContextHolder.get().enable());
            assertEquals(Set.of(10L), ((ProjectMemberRoleUserPageQuery) call.getArgument(0)).matchedDepartmentIds());
            return 1L;
        });
        when(users.selectPage(any())).thenReturn(List.of(new AdminUserDO().setId(9L).setDeptId(10L)));
        DataPermissionUtils.executeIgnore(() -> {
            assertEquals("可见部门", proxy.page(query("部门")).getList().getFirst().deptName());
            assertFalse(DataPermissionContextHolder.get().enable());
        });
        assertNull(DataPermissionContextHolder.get());
        verify(departments, times(1)).selectProjectMemberDepartments(any());
    }

    @Test
    void hiddenDepartmentsDoNotBecomeKeywordMatchesOrResponseNames() {
        when(users.selectDepartmentIds(any())).thenReturn(Set.of(20L));
        when(departments.selectProjectMemberDepartments(any())).thenReturn(List.of());
        when(users.selectCount(any())).thenReturn(1L);
        when(users.selectPage(any())).thenReturn(List.of(new AdminUserDO().setId(9L).setDeptId(20L)));
        assertNull(api.page(query("account")).getList().getFirst().deptName());
        verify(users).selectCount(argThat(request -> request.matchedDepartmentIds().isEmpty()));
    }

    @Test
    void failedRoleQueryRestoresDataPermissionContextWithoutDirectoryFallback() {
        when(users.selectCount(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> api.page(query(null)));
        assertNull(DataPermissionContextHolder.get());
        verify(users, never()).selectPage(any());
        verifyNoInteractions(departments);
    }
}
