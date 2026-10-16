package cn.iocoder.yudao.module.system.service.permission;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserRoleDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PermissionBatchQueryServiceTest {
    private final PermissionServiceImpl permissions = new PermissionServiceImpl();
    private final RoleService roles = mock(RoleService.class);
    private final MenuService menus = mock(MenuService.class);
    private final UserRoleMapper userRoles = mock(UserRoleMapper.class);
    private final RoleMenuMapper roleMenus = mock(RoleMenuMapper.class);
    private final PermissionBatchQueryService service = new PermissionBatchQueryService(permissions, roles);
    private static final Set<String> CODES = Set.of("note:query", "note:update", "note:missing");

    @BeforeEach void configure() {
        ReflectionTestUtils.setField(permissions, "roleService", roles);
        ReflectionTestUtils.setField(permissions, "menuService", menus);
        ReflectionTestUtils.setField(permissions, "userRoleMapper", userRoles);
        ReflectionTestUtils.setField(permissions, "roleMenuMapper", roleMenus);
        ReflectionTestUtils.setField(permissions, "associations", new PermissionAssociationService(roleMenus, userRoles));
    }
    private RoleDO role(long id, int status) {
        var role = new RoleDO(); role.setId(id); role.setStatus(status); return role;
    }
    private void memberships() {
        when(userRoles.selectListByUserId(42L)).thenReturn(List.of(
                new UserRoleDO().setUserId(42L).setRoleId(11L), new UserRoleDO().setUserId(42L).setRoleId(12L)));
        when(roles.getRoleListFromCache(Set.of(11L, 12L))).thenAnswer(ignored -> new ArrayList<>(List.of(role(11L, 0), role(12L, 1))));
    }
    private void policyMenus() {
        when(menus.getMenuIdListByPermissionFromCache("note:query")).thenReturn(List.of(100L));
        when(menus.getMenuIdListByPermissionFromCache("note:update")).thenReturn(List.of(101L));
        when(roleMenus.selectListByMenuId(100L)).thenReturn(List.of(new RoleMenuDO().setRoleId(11L).setMenuId(100L)));
        when(roleMenus.selectListByMenuId(101L)).thenReturn(List.of(new RoleMenuDO().setRoleId(12L).setMenuId(101L)));
    }
    @Test void evaluatesEachCodeWithTheExistingPolicyButLoadsRolesOnce() {
        memberships(); policyMenus();
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissions);
            assertEquals(Map.of("note:query", true, "note:update", false, "note:missing", false), service.queryPermissions(42L, CODES));
            verify(roles).getRoleListFromCache(Set.of(11L, 12L));
            verify(roles).hasAnySuperAdmin(Set.of(11L));
            verify(menus).getMenuIdListByPermissionFromCache("note:query");
            verify(menus).getMenuIdListByPermissionFromCache("note:update");
            verify(menus).getMenuIdListByPermissionFromCache("note:missing");
        }
    }
    @Test void preservesTheExistingSuperAdminFallbackForMissingMenus() {
        memberships();
        when(roles.hasAnySuperAdmin(Set.of(11L))).thenReturn(true);
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissions);
            assertEquals(Map.of("note:query", true, "note:update", true, "note:missing", true), service.queryPermissions(42L, CODES));
            verify(roles).getRoleListFromCache(Set.of(11L, 12L));
            verify(roles).hasAnySuperAdmin(Set.of(11L));
        }
    }
    @Test void batchReadsUseTheConfiguredTargetThroughTheSpringProxy() {
        memberships(); policyMenus();
        var factory = new ProxyFactory(permissions);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> invocation.proceed());
        var proxy = (PermissionServiceImpl) factory.getProxy();
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(proxy);
            assertEquals(Map.of("note:query", true, "note:update", false, "note:missing", false),
                    new PermissionBatchQueryService(proxy, roles).queryPermissions(42L, CODES));
            verify(roles).getRoleListFromCache(Set.of(11L, 12L));
            verify(roles).hasAnySuperAdmin(Set.of(11L));
        }
    }
    @Test void noEnabledRoleDeniesEveryCodeWithoutQueryingMenusOrSuperAdmin() {
        memberships();
        when(roles.getRoleListFromCache(Set.of(11L, 12L))).thenAnswer(ignored -> new ArrayList<>(List.of(role(11L, 1), role(12L, 1))));
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissions);
            assertEquals(Map.of("note:query", false, "note:update", false, "note:missing", false), service.queryPermissions(42L, CODES));
            verify(roles).getRoleListFromCache(Set.of(11L, 12L));
            verify(roles, never()).hasAnySuperAdmin(any());
            verifyNoInteractions(menus, roleMenus);
        }
    }
    @Test void theNextObservationRechecksRevocationAndAnotherUser() {
        memberships(); policyMenus();
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissions);
            assertTrue(service.queryPermissions(42L, Set.of("note:query")).get("note:query"));
            when(roles.getRoleListFromCache(Set.of(11L, 12L))).thenAnswer(ignored -> new ArrayList<>(List.of(role(11L, 1), role(12L, 1))));
            assertFalse(service.queryPermissions(42L, Set.of("note:query")).get("note:query"));
            assertFalse(service.queryPermissions(43L, Set.of("note:query")).get("note:query"));
            verify(roles, times(2)).getRoleListFromCache(Set.of(11L, 12L));
            verify(userRoles).selectListByUserId(43L);
        }
    }
    @Test void dependencyFailurePropagatesInsteadOfGrantingAccess() {
        memberships();
        when(menus.getMenuIdListByPermissionFromCache("note:query")).thenThrow(new IllegalStateException("cache unavailable"));
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissions);
            assertThrows(IllegalStateException.class, () -> service.queryPermissions(42L, Set.of("note:query")));
            verify(roles, never()).hasAnySuperAdmin(any());
        }
    }
    @Test void emptyAndInvalidQueriesCannotGrantPermission() {
        assertEquals(Map.of(), service.queryPermissions(42L, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> service.queryPermissions(null, CODES));
        assertThrows(IllegalArgumentException.class, () -> service.queryPermissions(0L, CODES));
        assertThrows(IllegalArgumentException.class, () -> service.queryPermissions(42L, Set.of(" ")));
        assertThrows(IllegalArgumentException.class, () -> service.queryPermissions(42L, null));
        verifyNoInteractions(roles, menus, userRoles, roleMenus);
    }
}
