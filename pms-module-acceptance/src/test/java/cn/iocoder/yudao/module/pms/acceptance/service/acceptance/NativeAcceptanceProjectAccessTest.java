package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NativeAcceptanceProjectAccessTest {
    @Mock private ProjectScopeApi scopes;

    private NativeAcceptanceProjectAccess access;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(10L).setTenantId(7L).setUserType(2),
                new MockHttpServletRequest());
        access = new NativeAcceptanceProjectAccess(scopes);
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingLoginIsDeniedBeforeScopeResolution() {
        SecurityContextHolder.clearContext();

        assertEquals(403, assertThrows(ServiceException.class, () -> access.requireRead(20L)).getCode());
        verifyNoInteractions(scopes);
    }

    @Test
    void missingProjectFromFullScopeAndPlaceholderProjectAreDenied() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(), Set.of()));
        assertThrows(ServiceException.class, () -> access.requireRead(20L));

        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(), Set.of(20L)));
        assertThrows(ServiceException.class, () -> access.requireWrite(20L));
        verify(scopes, never()).lockAndRevalidate(any());
    }

    @Test
    void writeRejectsTreeVersionChangedDuringRevalidation() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of()));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(20L, 4L, Set.of(20L), Set.of()));

        assertThrows(ServiceException.class, () -> access.requireWrite(20L));
        verify(scopes).lockAndRevalidate(ProjectScopeRevalidationQuery.builder()
                .tenantId(7L).subjectUserId(10L).anchorProjectId(20L)
                .actionCode(ProjectScopeApi.ACTION_MANAGE).expectedScopeVersion(3L).build());
    }

    @Test
    void readableProjectsDefaultsToEmptyAndPositiveScopeAllowsReadAndWrite() {
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of());
        assertEquals(Set.of(), access.readableProjects());
        verify(scopes).resolveAllCurrent(new ProjectAllScopeQuery(7L, 10L, ProjectScopeApi.ACTION_VIEW));

        var allowed = new ProjectScopeResult(20L, 3L, Set.of(20L), Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(allowed);
        when(scopes.lockAndRevalidate(any())).thenReturn(allowed);
        assertDoesNotThrow(() -> access.requireRead(20L));
        assertDoesNotThrow(() -> access.requireWrite(20L));
        verify(scopes).resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(7L).subjectUserId(10L).anchorProjectId(20L)
                .actionCode(ProjectScopeApi.ACTION_VIEW).build());
        verify(scopes).resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(7L).subjectUserId(10L).anchorProjectId(20L)
                .actionCode(ProjectScopeApi.ACTION_MANAGE).build());
    }
}
