package cn.iocoder.yudao.module.system.service.permission;

import cn.hutool.extra.spring.SpringUtil;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleMenuDO;
import cn.iocoder.yudao.module.system.dal.dataobject.permission.UserRoleDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.permission.RoleMenuMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.UserRoleMapper;
import cn.iocoder.yudao.module.system.enums.permission.DataScopeEnum;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomLongId;
import static cn.iocoder.yudao.framework.test.core.util.RandomUtils.randomPojo;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link PermissionServiceImpl} 的单元测试类
 *
 * 基于 Mockito 的纯单元测试，不依赖数据库
 */
public class PermissionServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PermissionServiceImpl permissionService;

    @Mock
    private RoleMenuMapper roleMenuMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private RoleService roleService;
    @Mock
    private MenuService menuService;
    @Mock
    private DeptService deptService;
    @Mock
    private AdminUserService userService;

    @Test
    public void testHasAnyPermissions_empty() {
        // 调用，空权限直接返回 true
        assertTrue(permissionService.hasAnyPermissions(randomLongId()));
    }

    @Test
    public void testHasAnyPermissions_noRoles() {
        Long userId = randomLongId();
        // mock 用户没有角色
        when(userRoleMapper.selectListByUserId(userId)).thenReturn(Collections.emptyList());
        when(roleService.getRoleListFromCache(any())).thenReturn(Collections.emptyList());

        try (MockedStatic<SpringUtil> springUtilMock = Mockito.mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissionService);

            // 调用，断言无权限
            assertFalse(permissionService.hasAnyPermissions(userId, "system:user:add"));
        }
    }

    @Test
    public void testHasAnyPermissions_hasPermission() {
        Long userId = randomLongId();
        Long roleId = randomLongId();
        Long menuId = randomLongId();
        // mock 用户角色
        UserRoleDO userRole = new UserRoleDO().setUserId(userId).setRoleId(roleId);
        when(userRoleMapper.selectListByUserId(userId)).thenReturn(Collections.singletonList(userRole));
        // mock 角色（启用状态）
        RoleDO role = randomPojo(RoleDO.class, o -> {
            o.setId(roleId);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
        });
        when(roleService.getRoleListFromCache(any())).thenReturn(new ArrayList<>(Collections.singletonList(role)));
        // mock 权限对应的菜单
        when(menuService.getMenuIdListByPermissionFromCache("system:user:add"))
                .thenReturn(Collections.singletonList(menuId));
        // mock 菜单对应的角色
        RoleMenuDO roleMenu = new RoleMenuDO().setRoleId(roleId).setMenuId(menuId);
        when(roleMenuMapper.selectListByMenuId(menuId)).thenReturn(Collections.singletonList(roleMenu));

        try (MockedStatic<SpringUtil> springUtilMock = Mockito.mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissionService);

            // 调用，断言有权限
            assertTrue(permissionService.hasAnyPermissions(userId, "system:user:add"));
        }
    }

    @Test
    public void testGetUserRoleIdListByUserId() {
        Long userId = randomLongId();
        Long roleId1 = randomLongId();
        Long roleId2 = randomLongId();
        // mock
        UserRoleDO userRole1 = new UserRoleDO().setUserId(userId).setRoleId(roleId1);
        UserRoleDO userRole2 = new UserRoleDO().setUserId(userId).setRoleId(roleId2);
        when(userRoleMapper.selectListByUserId(userId)).thenReturn(Arrays.asList(userRole1, userRole2));

        // 调用
        Set<Long> roleIds = permissionService.getUserRoleIdListByUserId(userId);
        // 断言
        assertEquals(2, roleIds.size());
        assertTrue(roleIds.contains(roleId1));
        assertTrue(roleIds.contains(roleId2));
    }

    @Test
    public void testAssignRoleMenu() {
        Long roleId = randomLongId();
        Long menuId1 = randomLongId(); // 已有的、需要保留
        Long menuId2 = randomLongId(); // 已有的、需要删除
        Long menuId3 = randomLongId(); // 新增的
        // mock 已有的角色菜单
        RoleMenuDO existMenu1 = new RoleMenuDO().setRoleId(roleId).setMenuId(menuId1);
        RoleMenuDO existMenu2 = new RoleMenuDO().setRoleId(roleId).setMenuId(menuId2);
        when(roleMenuMapper.selectListByRoleId(roleId)).thenReturn(Arrays.asList(existMenu1, existMenu2));

        // 调用：保留 menuId1，删除 menuId2，新增 menuId3
        permissionService.assignRoleMenu(roleId, new HashSet<>(Arrays.asList(menuId1, menuId3)));

        // 断言：新增了 menuId3
        verify(roleMenuMapper).insertBatch(argThat(list -> list.size() == 1
                && list.iterator().next().getRoleId().equals(roleId)
                && list.iterator().next().getMenuId().equals(menuId3)));
        // 断言：删除了 menuId2
        verify(roleMenuMapper).deleteListByRoleIdAndMenuIds(eq(roleId), argThat(ids -> ids.contains(menuId2) && !ids.contains(menuId1)));
    }

    @Test
    public void testAssignUserRole() {
        Long userId = randomLongId();
        Long roleId1 = randomLongId(); // 已有的、需要保留
        Long roleId2 = randomLongId(); // 已有的、需要删除
        Long roleId3 = randomLongId(); // 新增的
        // mock 已有的用户角色
        UserRoleDO existRole1 = new UserRoleDO().setUserId(userId).setRoleId(roleId1);
        UserRoleDO existRole2 = new UserRoleDO().setUserId(userId).setRoleId(roleId2);
        when(userRoleMapper.selectListByUserId(userId)).thenReturn(Arrays.asList(existRole1, existRole2));

        // 调用：保留 roleId1，删除 roleId2，新增 roleId3
        permissionService.assignUserRole(userId, new HashSet<>(Arrays.asList(roleId1, roleId3)));

        // 断言：新增了 roleId3
        verify(userRoleMapper).insertBatch(argThat(list -> list.size() == 1
                && list.iterator().next().getUserId().equals(userId)
                && list.iterator().next().getRoleId().equals(roleId3)));
        // 断言：删除了 roleId2
        verify(userRoleMapper).deleteListByUserIdAndRoleIdIds(eq(userId), argThat(ids -> ids.contains(roleId2) && !ids.contains(roleId1)));
    }

    // ========== 数据权限的相关方法 ==========

    @Test
    public void testGetDeptDataPermission_all() {
        Long userId = randomLongId();
        Long roleId = randomLongId();
        // mock 用户角色
        UserRoleDO userRole = new UserRoleDO().setUserId(userId).setRoleId(roleId);
        when(userRoleMapper.selectListByUserId(userId)).thenReturn(Collections.singletonList(userRole));
        // mock 角色（启用 + 全部数据权限）
        RoleDO role = randomPojo(RoleDO.class, o -> {
            o.setId(roleId);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setDataScope(DataScopeEnum.ALL.getScope());
        });
        when(roleService.getRoleListFromCache(any())).thenReturn(new ArrayList<>(Collections.singletonList(role)));

        try (MockedStatic<SpringUtil> springUtilMock = Mockito.mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissionService);

            // 调用
            DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(userId);
            // 断言：all=true
            assertTrue(result.getAll());
            assertFalse(result.getSelf());
            assertTrue(result.getDeptIds().isEmpty());
        }
    }

    @Test
    public void testGetDeptDataPermission_deptOnly() {
        Long userId = randomLongId();
        Long roleId = randomLongId();
        Long deptId = randomLongId();
        // mock 用户角色
        UserRoleDO userRole = new UserRoleDO().setUserId(userId).setRoleId(roleId);
        when(userRoleMapper.selectListByUserId(userId)).thenReturn(Collections.singletonList(userRole));
        // mock 角色（启用 + 本部门数据权限）
        RoleDO role = randomPojo(RoleDO.class, o -> {
            o.setId(roleId);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setDataScope(DataScopeEnum.DEPT_ONLY.getScope());
        });
        when(roleService.getRoleListFromCache(any())).thenReturn(new ArrayList<>(Collections.singletonList(role)));
        // mock 用户所属部门
        AdminUserDO user = new AdminUserDO().setDeptId(deptId);
        when(userService.getUser(userId)).thenReturn(user);

        try (MockedStatic<SpringUtil> springUtilMock = Mockito.mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissionService);

            // 调用
            DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(userId);
            // 断言：deptIds 包含用户部门编号
            assertFalse(result.getAll());
            assertFalse(result.getSelf());
            assertEquals(1, result.getDeptIds().size());
            assertTrue(result.getDeptIds().contains(deptId));
        }
    }

    @Test
    public void testGetDeptDataPermission_self() {
        Long userId = randomLongId();
        Long roleId = randomLongId();
        // mock 用户角色
        UserRoleDO userRole = new UserRoleDO().setUserId(userId).setRoleId(roleId);
        when(userRoleMapper.selectListByUserId(userId)).thenReturn(Collections.singletonList(userRole));
        // mock 角色（启用 + 仅本人数据权限）
        RoleDO role = randomPojo(RoleDO.class, o -> {
            o.setId(roleId);
            o.setStatus(CommonStatusEnum.ENABLE.getStatus());
            o.setDataScope(DataScopeEnum.SELF.getScope());
        });
        when(roleService.getRoleListFromCache(any())).thenReturn(new ArrayList<>(Collections.singletonList(role)));

        try (MockedStatic<SpringUtil> springUtilMock = Mockito.mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissionService);

            // 调用
            DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(userId);
            // 断言：self=true
            assertFalse(result.getAll());
            assertTrue(result.getSelf());
            assertTrue(result.getDeptIds().isEmpty());
        }
    }

    @Test
    public void testGetDeptDataPermission_noRole() {
        Long userId = randomLongId();
        // mock 用户没有角色
        when(userRoleMapper.selectListByUserId(userId)).thenReturn(Collections.emptyList());
        when(roleService.getRoleListFromCache(any())).thenReturn(Collections.emptyList());

        try (MockedStatic<SpringUtil> springUtilMock = Mockito.mockStatic(SpringUtil.class)) {
            springUtilMock.when(() -> SpringUtil.getBean(PermissionServiceImpl.class)).thenReturn(permissionService);

            // 调用
            DeptDataPermissionRespDTO result = permissionService.getDeptDataPermission(userId);
            // 断言：self=true（无角色时只能查看自己）
            assertFalse(result.getAll());
            assertTrue(result.getSelf());
            assertTrue(result.getDeptIds().isEmpty());
        }
    }

}
