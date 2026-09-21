package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectDeliverableAccessTest {
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectDeliverableAccess access = new ProjectDeliverableAccess(scopes, permissions);
    ProjectDeliverableRuleApi.Context context(String status) {
        return new ProjectDeliverableRuleApi.Context(9L, 10L, status, 11L, "S1", null, JsonUtils.parseTree("{}"));
    }
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        when(permissions.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(true);
        var scope = new ProjectScopeResult(9L, 2L, Set.of(9L), Set.of());
        when(scopes.resolveCurrent(any())).thenReturn(scope); when(scopes.lockAndRevalidate(any())).thenReturn(scope);
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void managerInManageScopeCanSubmitAndReadRemainsAvailableAfterClosure() {
        assertEquals(2L, access.check(context("ACTIVE"), 7L, 11L, true, true, 2L));
        assertEquals(2L, access.check(context("NORMAL_CLOSED"), 7L, 12L, false, false, null));
    }
    @Test void manageScopeCanSubmitWithoutBeingManagerButClosureStillBlocksWrites() {
        assertEquals(2L, access.check(context("ACTIVE"), 7L, 12L, true, true, 2L));
        var unassigned = new ProjectDeliverableRuleApi.Context(9L, 10L, "ACTIVE", null, "S1", null, JsonUtils.parseTree("{}"));
        assertEquals(2L, access.check(unassigned, 7L, 12L, true, true, 2L));
        assertThrows(RuntimeException.class, () -> access.check(context("NORMAL_CLOSED"), 7L, 11L, true, true, 2L));
    }
    @Test void emptyScopeAndStaleScopeCannotBroadenPermission() {
        assertThrows(RuntimeException.class, () -> access.check(context("ACTIVE"), 7L, 11L, true, true, 1L));
        when(scopes.lockAndRevalidate(any())).thenReturn(new ProjectScopeResult(9L, 2L, Set.of(), Set.of(9L)));
        assertThrows(RuntimeException.class, () -> access.check(context("ACTIVE"), 7L, 11L, true, true, 2L));
    }
    @Test void tenantAndMissingFunctionalPermissionFail() {
        assertThrows(RuntimeException.class, () -> access.check(context("ACTIVE"), 8L, 11L, true, true, 2L));
        when(permissions.hasAnyPermissions(anyLong(), any(String[].class))).thenReturn(false);
        assertThrows(RuntimeException.class, () -> access.check(context("ACTIVE"), 7L, 11L, true, true, 2L));
    }
}
