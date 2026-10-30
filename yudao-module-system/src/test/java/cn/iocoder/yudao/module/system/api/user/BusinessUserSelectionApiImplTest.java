package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionAnnotationAdvisor;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.user.UserController;
import cn.iocoder.yudao.module.system.dal.dataobject.dept.DeptDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.service.dept.DeptService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BusinessUserSelectionApiImplTest {
    AdminUserService users;
    DeptService departments;
    BusinessUserSelectionApi api;

    @BeforeEach void setup() {
        users = mock(AdminUserService.class);
        departments = mock(DeptService.class);
        var factory = new ProxyFactory(new BusinessUserSelectionApiImpl(users, departments));
        factory.setProxyTargetClass(true);
        factory.addAdvisor(new DataPermissionAnnotationAdvisor());
        api = (BusinessUserSelectionApi) factory.getProxy();
        TenantContextHolder.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(7L).setTenantId(1L), new MockHttpServletRequest());
    }

    @AfterEach void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        DataPermissionContextHolder.clear();
    }

    @Test void matchesEmailOnOnlyTheOriginalEnabledCandidatesWithoutReturningEmail() {
        var first = user(2L, "z00001", "张三甲", "ZhangSan.first@example.test", 10L);
        var second = user(3L, "z00002", "张三乙", "zhangsan.second@example.test", 10L);
        when(users.getUserListByStatus(0)).thenReturn(List.of(first, second, user(4L, "l00001", "李四", null, null)));
        when(departments.getDeptMap(any())).thenReturn(Map.of(10L, dept(10L, "交付团队")));
        var result = api.page(new BusinessUserSelectionApi.Query(2, 1, " ZHANGSAN "));
        assertEquals(2L, result.getTotal());
        assertEquals(List.of(new BusinessUserSelectionApi.Option(3L, "张三乙", 10L, "交付团队")), result.getList());
        assertEquals(List.of("id", "nickname", "deptId", "deptName"), Arrays.stream(
                BusinessUserSelectionApi.Option.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
        verify(users).getUserListByStatus(0);
        verifyNoMoreInteractions(users);
        assertTrue(api.page(new BusinessUserSelectionApi.Query(1, 20, "outside-scope-email")).getList().isEmpty());
    }

    @Test void keepsOriginalFullCandidateSetNameAccountAndVisibleDepartmentSearchAndPages() {
        var one = user(2L, "a00001", "张三", "zhangsan@example.test", 10L);
        var two = user(3L, "b00001", "李四", null, 20L);
        when(users.getUserListByStatus(0)).thenReturn(List.of(one, two));
        when(departments.getDeptMap(any())).thenReturn(Map.of(10L, dept(10L, "交付团队")));

        var old = new UserController();
        ReflectionTestUtils.setField(old, "userService", users);
        ReflectionTestUtils.setField(old, "deptService", departments);
        assertEquals(old.getSimpleUserList(null).getData().stream().map(row -> row.getId()).toList(),
                api.page(new BusinessUserSelectionApi.Query(1, 20, "")).getList().stream().map(BusinessUserSelectionApi.Option::id).toList());
        for (String keyword : List.of("张三", "A00001", "交付团队", "zhangsan@example.test"))
            assertEquals(List.of(2L), api.page(new BusinessUserSelectionApi.Query(1, 20, keyword))
                    .getList().stream().map(BusinessUserSelectionApi.Option::id).toList());
        assertEquals(List.of(3L), api.page(new BusinessUserSelectionApi.Query(2, 1, " "))
                .getList().stream().map(BusinessUserSelectionApi.Option::id).toList());
        assertTrue(api.page(new BusinessUserSelectionApi.Query(Integer.MAX_VALUE, 100, "")).getList().isEmpty());
    }

    @Test void emptyOriginalScopeDoesNotResolveOrEnrichAnyone() {
        when(users.getUserListByStatus(0)).thenReturn(List.of());
        assertTrue(api.page(new BusinessUserSelectionApi.Query(1, 20, "zhangsan")).getList().isEmpty());
        verifyNoInteractions(departments);
        verify(users).getUserListByStatus(0);
        verifyNoMoreInteractions(users);
    }

    @Test void resolvesOnlyRequestedIdentifiersInsideTheSameDirectoryScope() {
        when(users.getUserListByStatus(0)).thenReturn(List.of(user(2L, "visible", "可见", null, null)));
        when(departments.getDeptMap(any())).thenReturn(Map.of());
        assertEquals(List.of(2L), api.page(new BusinessUserSelectionApi.Query(1, 100, "", Set.of(2L, 99L)))
                .getList().stream().map(BusinessUserSelectionApi.Option::id).toList());
        assertTrue(api.page(new BusinessUserSelectionApi.Query(1, 100, "", Set.of(99L))).getList().isEmpty());
        verify(users, never()).getUserList(any());
    }

    @Test void explicitlyEmptyIdentifierFilterNeverLoadsAnUnboundedDirectory() {
        assertTrue(api.page(new BusinessUserSelectionApi.Query(1, 100, "", Set.of())).getList().isEmpty());
        verifyNoInteractions(users, departments);
    }

    @Test void forcesOrdinaryUserAndDepartmentPermissionsEvenInsideIgnoringCallerAndRestoresIt() {
        when(users.getUserListByStatus(0)).thenAnswer(call -> {
            assertTrue(DataPermissionContextHolder.get().enable());
            assertEquals(1L, TenantContextHolder.getTenantId());
            return List.of(user(2L, "visible", "可见", "visible@example.test", 10L));
        });
        when(departments.getDeptMap(any())).thenAnswer(call -> {
            assertTrue(DataPermissionContextHolder.get().enable());
            return Map.of();
        });
        DataPermissionUtils.executeIgnore(() -> {
            assertFalse(DataPermissionContextHolder.get().enable());
            assertEquals(1L, api.page(new BusinessUserSelectionApi.Query(1, 20, "visible")).getTotal());
            assertFalse(DataPermissionContextHolder.get().enable());
        });
        assertNull(DataPermissionContextHolder.get());
    }

    @Test void missingOrForeignTenantActorCannotReadAnyDirectoryFacts() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(ServiceException.class, () -> api.page(new BusinessUserSelectionApi.Query(1, 20, null)));
        TenantContextHolder.setTenantId(1L);
        SecurityContextHolder.clearContext();
        assertThrows(ServiceException.class, () -> api.page(new BusinessUserSelectionApi.Query(1, 20, null)));
        verifyNoInteractions(users, departments);
    }

    @Test void invalidPagingAndKeywordRejectBeforeAnyRead() {
        for (var query : List.of(new BusinessUserSelectionApi.Query(0, 20, null),
                new BusinessUserSelectionApi.Query(1, 0, null), new BusinessUserSelectionApi.Query(1, 101, null),
                new BusinessUserSelectionApi.Query(1, 20, "x".repeat(65))))
            assertThrows(ServiceException.class, () -> api.page(query));
        assertThrows(ServiceException.class, () -> api.page(null));
        verifyNoInteractions(users, departments);
    }

    @Test void directoryFailureNeverReturnsFallbackCandidatesAndRestoresPermissionContext() {
        when(users.getUserListByStatus(0)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class, () -> api.page(new BusinessUserSelectionApi.Query(1, 20, null)));
        assertNull(DataPermissionContextHolder.get());
        verifyNoInteractions(departments);
    }

    private static AdminUserDO user(Long id, String username, String nickname, String email, Long deptId) {
        var user = new AdminUserDO();
        user.setId(id); user.setUsername(username); user.setNickname(nickname); user.setEmail(email);
        user.setDeptId(deptId); user.setStatus(0);
        return user;
    }
    private static DeptDO dept(Long id, String name) {
        var dept = new DeptDO(); dept.setId(id); dept.setName(name); return dept;
    }
}
