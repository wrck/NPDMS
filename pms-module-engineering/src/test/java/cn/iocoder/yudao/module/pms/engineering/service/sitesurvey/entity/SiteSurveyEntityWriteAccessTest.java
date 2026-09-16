package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteSurveyEntityWriteAccessTest {
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final ProjectBusinessExecutionApi executions = mock(ProjectBusinessExecutionApi.class);
    final SiteSurveyEntityWriteAccess access = new SiteSurveyEntityWriteAccess(permissions,scopes,executions);
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L); var user = new LoginUser(); user.setId(8L); user.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(user,new MockHttpServletRequest());
        when(permissions.hasAnyPermissions(8L,"pms:eng-site-survey:create")).thenReturn(true);
        when(scopes.resolveCurrent(new ProjectCurrentScopeQuery(7L,8L,9L,ProjectScopeApi.ACTION_MANAGE)))
                .thenReturn(new ProjectScopeResult(9L,1L,Set.of(9L),Set.of()));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); SecurityContextHolder.clearContext(); }
    @Test void usesTrustedActorAndOwnerIdentityForStandaloneWrite() {
        access.lock(9L,"pms:eng-site-survey:create",null);
        verify(executions).lockForWrite(new ProjectBusinessExecutionApi.WriteRequest(9L,"SOL","SITE_SURVEY",null));
    }
    @Test void missingFunctionalPermissionOrEmptyScopeNeverInvokesExecutionApi() {
        assertThrows(ServiceException.class, () -> access.lock(9L,"pms:eng-site-survey:delete",null));
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(ServiceException.class, () -> access.lock(9L,"pms:eng-site-survey:create",null));
        verifyNoInteractions(executions);
    }
    @Test void unauthenticatedOrUnavailableExecutionDoesNotFallBackToOriginalWrite() {
        doThrow(new IllegalStateException("node unavailable")).when(executions).lockForWrite(any());
        assertThrows(IllegalStateException.class, () -> access.lock(9L,"pms:eng-site-survey:create",null));
        SecurityContextHolder.clearContext(); assertThrows(ServiceException.class, () -> access.lock(9L,"pms:eng-site-survey:create",null));
        verify(executions,times(1)).lockForWrite(any());
    }
}
