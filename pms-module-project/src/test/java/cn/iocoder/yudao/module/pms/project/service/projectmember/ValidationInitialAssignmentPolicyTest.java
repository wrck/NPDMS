package cn.iocoder.yudao.module.pms.project.service.projectmember;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.projectauthorization.ProjectAuthorizationGuard;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ValidationInitialAssignmentPolicyTest {
    final MockEnvironment environment = new MockEnvironment();
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectMemberAssignmentMapper members = mock(ProjectMemberAssignmentMapper.class);
    final ValidationInitialAssignmentPolicy policy = new ValidationInitialAssignmentPolicy(environment, permissions, members);
    final ProjectAuthorizationGuard.Actor actor = new ProjectAuthorizationGuard.Actor(1L, 7L);
    final ProjectMasterDO project = new ProjectMasterDO();
    final LoginUser login = new LoginUser();

    @BeforeEach void setUp() {
        environment.setProperty(ValidationInitialAssignmentPolicy.DATASOURCE_URL_PROPERTY,
                "jdbc:mysql://localhost:3306/npdms_template_core_20260910?useSSL=false");
        project.setId(9L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        project.setCurrentStage("S0"); project.setAssignmentStatus("UNASSIGNED");
        login.setId(7L); login.setTenantId(1L); login.setUserType(UserTypeEnum.ADMIN.getValue());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(login, null, List.of()));
        TenantContextHolder.setTenantId(1L);
        when(permissions.hasAnyRoles(7L, "super_admin")).thenReturn(true);
        when(members.selectActiveForAssignmentState(any())).thenReturn(List.of());
    }

    @AfterEach void cleanUp() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    void enable() { environment.setProperty(ValidationInitialAssignmentPolicy.ENABLED_PROPERTY, "true"); }

    @Test void defaultsToDisabled() {
        assertFalse(policy.permitsCandidates(actor, project));
        verifyNoInteractions(members);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:mysql://localhost:3306/npdms", "jdbc:mysql://localhost:3306/npdms_template_core_20260910_prod",
            "jdbc:mysql://localhost:3306/npdms_template_core_20260910/other", "jdbc:mysql://localhost:3306/prod?db=npdms_template_core_20260910",
            "jdbc:mysql://localhost:3306/npdms_template_core_20260910#prod", "", "not-a-url"})
    void wrongDatabaseFailsClosed(String url) {
        enable(); environment.setProperty(ValidationInitialAssignmentPolicy.DATASOURCE_URL_PROPERTY, url);
        assertFalse(policy.permitsCandidates(actor, project));
    }

    @Test void requiresActualSuperAdminActorInSameTenant() {
        enable(); assertTrue(policy.permitsCandidates(actor, project));
        when(permissions.hasAnyRoles(7L, "super_admin")).thenReturn(false);
        assertFalse(policy.permitsCandidates(actor, project));
        when(permissions.hasAnyRoles(7L, "super_admin")).thenReturn(true);
        assertFalse(policy.permitsCandidates(new ProjectAuthorizationGuard.Actor(1L, 8L), project));
        project.setTenantId(2L); assertFalse(policy.permitsCandidates(actor, project)); project.setTenantId(1L);
        TenantContextHolder.setTenantId(2L); assertFalse(policy.permitsCandidates(actor, project)); TenantContextHolder.setTenantId(1L);
        login.setTenantId(2L); assertFalse(policy.permitsCandidates(actor, project)); login.setTenantId(1L);
        SecurityContextHolder.clearContext(); assertFalse(policy.permitsCandidates(actor, project));
    }

    @Test void rejectsClosedNonS0AndAssignedProjects() {
        enable();
        for (String state : List.of("NORMAL_CLOSED", "NO_TRACKING_CLOSED", "EXCEPTION_CLOSED")) {
            project.setLifecycleStatus(state); assertFalse(policy.permitsCandidates(actor, project));
        }
        project.setLifecycleStatus("ACTIVE"); project.setCurrentStage("S1");
        assertFalse(policy.permitsCandidates(actor, project));
        project.setCurrentStage("S0"); project.setAssignmentStatus("ASSIGNED");
        assertFalse(policy.permitsCandidates(actor, project));
    }

    @Test void onlyMissingPrimaryMayBeAssignedAndBothPrimariesEndExceptionImmediately() {
        enable(); assertTrue(policy.permitsAssignment(actor, project, true, true));
        var sm = new ProjectMemberAssignmentDO(); sm.setTenantId(1L);
        sm.setMemberRole("SERVICE_MANAGER_L1"); sm.setAssignmentType("PRIMARY");
        when(members.selectActiveForAssignmentState(any())).thenReturn(List.of(sm));
        assertFalse(policy.permitsAssignment(actor, project, true, false));
        assertTrue(policy.permitsAssignment(actor, project, false, true));
        project.setManagerId(8L);
        assertFalse(policy.permitsCandidates(actor, project));
        assertFalse(policy.permitsAssignment(actor, project, false, true));
    }

    @Test void initialGuardAllowsAssignmentButNotOrdinaryManageOrAuthorizationGrants() {
        enable();
        var mapper = mock(ProjectMasterMapper.class);
        var versions = mock(ProjectTreeVersionMapper.class);
        var scope = mock(ProjectScopeApi.class);
        var guard = new ProjectAuthorizationGuard(permissions, mapper, members, versions,
                mock(ProjectTreePathMapper.class), scope);
        ReflectionTestUtils.setField(guard, "validationInitialAssignmentPolicy", policy);
        when(mapper.selectById(9L)).thenReturn(project);
        when(mapper.selectByIdForUpdate(9L)).thenReturn(project);
        guard.assertCanInitiallyAssign(actor, 9L, true, true);
        verifyNoInteractions(scope);
        var version = new ProjectTreeVersionDO(); version.setTreeVersion(1L);
        when(versions.selectLatestActive(9L)).thenReturn(version);
        when(scope.resolve(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(), Set.of()));
        assertThrows(ServiceException.class, () -> guard.assertCanAssign(actor, 9L));
        assertThrows(ServiceException.class, () -> guard.assertCanInitiallyAssign(actor, 9L, false, false));
        when(permissions.hasAnyPermissions(7L, ProjectAuthorizationGuard.PERMISSION_MANAGE)).thenReturn(true);
        var membership = new ProjectMemberAssignmentDO(); membership.setMemberRole("SERVICE_MANAGER_L1");
        when(members.selectActiveByUser(any())).thenReturn(List.of(membership));
        assertThrows(ServiceException.class, () -> guard.assertCanCreate(actor, 9L, "PROJECT_MANAGE", "CURRENT_PROJECT"));
        project.setAssignmentStatus("ASSIGNED");
        assertThrows(ServiceException.class, () -> guard.assertCanInitiallyAssign(actor, 9L, true, true));
    }
}
