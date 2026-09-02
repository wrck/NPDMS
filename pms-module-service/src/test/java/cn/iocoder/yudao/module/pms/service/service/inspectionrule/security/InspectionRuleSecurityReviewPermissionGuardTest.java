package cn.iocoder.yudao.module.pms.service.service.inspectionrule.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionRuleSecurityReviewPermissionGuardTest {

    private static final long TENANT_ID = 7L;
    private static final long ACTOR_ID = 9L;

    @Mock
    private InspectionRuleExplicitAuthorizationApi authorizationApi;

    private InspectionRuleSecurityReviewPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new InspectionRuleSecurityReviewPermissionGuard(authorizationApi);
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
    void shouldRemainOutsideSpringUntilExplicitAuthorizationAdapterExists() {
        assertNull(InspectionRuleSecurityReviewPermissionGuard.class.getAnnotation(Component.class));
        assertNull(InspectionRuleSecurityReviewPermissionGuard.class.getAnnotation(Service.class));
    }

    @Test
    void shouldRejectUnauthenticatedActorBeforeAuthorizationQuery() {
        SecurityContextHolder.clearContext();

        assertThrows(SecurityException.class, guard::check);

        verifyNoInteractions(authorizationApi);
    }

    @Test
    void shouldRejectMissingOrMismatchedExplicitAuthorization() {
        when(authorizationApi.findExplicitAuthorization(
                TENANT_ID,
                ACTOR_ID,
                InspectionRuleSecurityReviewPermissionGuard.REVIEW_PERMISSION))
                .thenReturn(null)
                .thenReturn(authorization(8L, ACTOR_ID, "pms:inspection-rule:security-review"))
                .thenReturn(authorization(TENANT_ID, 10L, "pms:inspection-rule:security-review"))
                .thenReturn(authorization(TENANT_ID, ACTOR_ID, "pms:inspection-rule:publish"));

        assertThrows(SecurityException.class, guard::check);
        assertThrows(SecurityException.class, guard::check);
        assertThrows(SecurityException.class, guard::check);
        assertThrows(SecurityException.class, guard::check);
    }

    @Test
    void shouldReturnExplicitAuthorizationWithoutInventedSource() {
        when(authorizationApi.findExplicitAuthorization(
                TENANT_ID,
                ACTOR_ID,
                InspectionRuleSecurityReviewPermissionGuard.REVIEW_PERMISSION))
                .thenReturn(authorization(TENANT_ID, ACTOR_ID,
                        InspectionRuleSecurityReviewPermissionGuard.REVIEW_PERMISSION));

        InspectionRuleSecurityReviewPermissionGuard.ReviewAuthorization authorization = guard.check();

        assertEquals(ACTOR_ID, authorization.actorId());
        assertEquals("pms:inspection-rule:security-review", authorization.permissionCode());
        assertEquals("RBAC_PERMISSION", authorization.authorizationType());
        assertNull(authorization.authorizationSourceId());
        verify(authorizationApi).findExplicitAuthorization(
                TENANT_ID,
                ACTOR_ID,
                InspectionRuleSecurityReviewPermissionGuard.REVIEW_PERMISSION);
    }

    private static InspectionRuleExplicitAuthorizationApi.ExplicitAuthorization authorization(
            Long tenantId,
            Long actorId,
            String permissionCode) {
        return new InspectionRuleExplicitAuthorizationApi.ExplicitAuthorization(
                tenantId,
                actorId,
                permissionCode,
                "RBAC_PERMISSION",
                null);
    }
}
