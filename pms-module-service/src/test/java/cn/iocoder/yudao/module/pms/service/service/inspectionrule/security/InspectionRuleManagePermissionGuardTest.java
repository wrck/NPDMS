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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionRuleManagePermissionGuardTest {

    private static final long TENANT_ID = 7L;
    private static final long ACTOR_ID = 9L;

    @Mock
    private PermissionApi permissionApi;

    private InspectionRuleManagePermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new InspectionRuleManagePermissionGuard(permissionApi);
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
    void shouldAllowAuthenticatedActorWithManagePermission() {
        when(permissionApi.hasAnyPermissions(ACTOR_ID, InspectionRuleManagePermissionGuard.MANAGE_PERMISSION))
                .thenReturn(true);

        guard.check();

        verify(permissionApi).hasAnyPermissions(ACTOR_ID, InspectionRuleManagePermissionGuard.MANAGE_PERMISSION);
    }

    @Test
    void shouldRejectUnauthenticatedActorBeforePermissionQuery() {
        SecurityContextHolder.clearContext();

        ServiceException failure = assertThrows(ServiceException.class, guard::check);

        assertEquals(1_013_002_008, failure.getCode());
        verifyNoInteractions(permissionApi);
    }

    @Test
    void shouldRejectActorWithoutManagePermission() {
        when(permissionApi.hasAnyPermissions(ACTOR_ID, InspectionRuleManagePermissionGuard.MANAGE_PERMISSION))
                .thenReturn(false);

        ServiceException failure = assertThrows(ServiceException.class, guard::check);

        assertEquals(1_013_002_008, failure.getCode());
    }
}
