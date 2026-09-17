package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证范围决策与重新核验；事务代理和数据库锁语义仍需集成测试。 */
class BriefingEntityAccessTest {
    private final PermissionApi permissions=mock(PermissionApi.class);
    private final ProjectScopeApi scopes=mock(ProjectScopeApi.class);
    private final BriefingEntityAccess access=new BriefingEntityAccess(permissions,scopes);
    private MockedStatic<SecurityFrameworkUtils> security;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        security=mockStatic(SecurityFrameworkUtils.class);
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
    }
    @AfterEach void cleanup() { security.close(); TenantContextHolder.clear(); }
    private ProjectScopeResult full(long version) { return new ProjectScopeResult(10L,version,Set.of(10L),Set.of()); }
    @Test void noFunctionPermissionDoesNotResolveProjectScope() {
        assertThrows(ServiceException.class, () -> access.requireReadable(10L)); verifyNoInteractions(scopes);
    }
    @Test void missingActorIsRejected() {
        security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(null);
        assertThrows(ServiceException.class,access::actorId); verifyNoInteractions(scopes,permissions);
    }
    @Test void placeholderProjectCannotBeEdited() {
        when(permissions.hasAnyPermissions(2L,BriefingEntityAccess.UPDATE)).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(10L,5L,Set.of(),Set.of(10L)));
        assertThrows(ServiceException.class, () -> access.lockWrite(10L,BriefingEntityAccess.UPDATE));
        verify(scopes,never()).lockAndRevalidate(any());
    }
    @Test void changedScopeVersionIsRejected() {
        when(permissions.hasAnyPermissions(2L,BriefingEntityAccess.UPDATE)).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(full(5)); when(scopes.lockAndRevalidate(any())).thenReturn(full(6));
        assertThrows(ServiceException.class, () -> access.lockWrite(10L,BriefingEntityAccess.UPDATE));
    }
    @Test void revokedProjectAfterLockIsRejected() {
        when(permissions.hasAnyPermissions(2L,BriefingEntityAccess.UPDATE)).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(full(5));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(10L,5L,Set.of(),Set.of()));
        assertThrows(ServiceException.class, () -> access.lockWrite(10L,BriefingEntityAccess.UPDATE));
    }
    @Test void matchingScopeRevalidatesExactVersion() {
        when(permissions.hasAnyPermissions(2L,BriefingEntityAccess.UPDATE)).thenReturn(true);
        when(scopes.resolveCurrent(any())).thenReturn(full(5)); when(scopes.lockAndRevalidate(any())).thenReturn(full(5));
        access.lockWrite(10L,BriefingEntityAccess.UPDATE);
        verify(scopes).lockAndRevalidate(new ProjectScopeRevalidationQuery(1L,2L,10L,ProjectScopeApi.ACTION_EDIT,5L));
        verify(permissions,times(2)).hasAnyPermissions(2L,BriefingEntityAccess.UPDATE);
    }
    @Test void emptyVisibleScopeStaysEmpty() {
        when(permissions.hasAnyPermissions(2L,BriefingEntityAccess.QUERY)).thenReturn(true);
        when(scopes.resolveAllCurrent(any())).thenReturn(Set.of()); assertEquals(Set.of(),access.visibleProjects());
    }
    @Test void nullVisibleScopeDoesNotBecomeUnrestricted() {
        when(permissions.hasAnyPermissions(2L,BriefingEntityAccess.QUERY)).thenReturn(true);
        assertThrows(ServiceException.class,access::visibleProjects);
    }
}
