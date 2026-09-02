package cn.iocoder.yudao.module.pms.service.service.inspectionrule.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class InspectionRuleSecurityReviewPermissionGuardTest {

    private static final long TENANT_ID = 7L;
    private static final long ACTOR_ID = 9L;

    @Mock
    private PermissionApi permissionApi;

    private InspectionRuleSecurityReviewPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new InspectionRuleSecurityReviewPermissionGuard(permissionApi);
        TenantContextHolder.setTenantId(TENANT_ID);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(ACTOR_ID);
        loginUser.setTenantId(TENANT_ID);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBeSpringServiceUsingExistingPermissionApi() {
        assertNotNull(InspectionRuleSecurityReviewPermissionGuard.class.getAnnotation(Service.class));
    }

    @Test
    void shouldRejectUnauthenticatedActorBeforePermissionQuery() {
        SecurityContextHolder.clearContext();

        ServiceException failure = assertThrows(ServiceException.class, guard::check);

        assertEquals(1_013_002_013, failure.getCode());
        verifyNoInteractions(permissionApi);
    }

    @Test
    void shouldRejectMissingTenantBeforePermissionQuery() {
        TenantContextHolder.clear();

        assertThrows(RuntimeException.class, guard::check);

        verifyNoInteractions(permissionApi);
    }

    @Test
    void shouldRejectDeniedOrFailedPermissionQuery() {
        when(permissionApi.hasAnyPermissions(
                ACTOR_ID,
                InspectionRuleSecurityReviewPermissionGuard.REVIEW_PERMISSION))
                .thenReturn(false)
                .thenThrow(new IllegalStateException("permission unavailable"));

        ServiceException denied = assertThrows(ServiceException.class, guard::check);
        ServiceException unavailable = assertThrows(ServiceException.class, guard::check);

        assertEquals(1_013_002_013, denied.getCode());
        assertEquals(1_013_002_013, unavailable.getCode());
    }

    @Test
    void shouldAllowBothRoleAndSuperAdminBooleanResultsWithoutInventedSource() {
        when(permissionApi.hasAnyPermissions(
                ACTOR_ID,
                InspectionRuleSecurityReviewPermissionGuard.REVIEW_PERMISSION))
                .thenReturn(true, true);

        InspectionRuleSecurityReviewPermissionGuard.ReviewAuthorization roleAuthorization = guard.check();
        InspectionRuleSecurityReviewPermissionGuard.ReviewAuthorization superAdminAuthorization = guard.check();

        assertEquals(ACTOR_ID, roleAuthorization.actorId());
        assertEquals("pms:inspection-rule:security-review", roleAuthorization.permissionCode());
        assertEquals("RBAC_PERMISSION", roleAuthorization.authorizationType());
        assertNull(roleAuthorization.authorizationSourceId());
        assertEquals(roleAuthorization, superAdminAuthorization);
        verify(permissionApi, times(2)).hasAnyPermissions(
                ACTOR_ID,
                InspectionRuleSecurityReviewPermissionGuard.REVIEW_PERMISSION);
    }
}
