package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.datapermission.core.aop.DataPermissionContextHolder;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.service.user.AdminUserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_IS_DISABLE;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class AdminUserApiIdentityValidationTest {
    private final AdminUserApiImpl api = new AdminUserApiImpl();
    private final AdminUserServiceImpl service = new AdminUserServiceImpl();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        AdminUserMapper mapper = mock(AdminUserMapper.class);
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(api, "userService", service);
        List<AdminUserDO> accounts = List.of(user(2L, 1L, 0), user(3L, 1L, 0),
                user(4L, 1L, 1), user(5L, 2L, 0));
        // Model the observed PM SELF scope while retaining the independent tenant boundary.
        when(mapper.selectByIds(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            DataPermission scope = DataPermissionContextHolder.get();
            boolean selfOnly = scope == null || scope.enable();
            return accounts.stream().filter(user -> ids.contains(user.getId()))
                    .filter(user -> Objects.equals(user.getTenantId(), TenantContextHolder.getTenantId()))
                    .filter(user -> !selfOnly || user.getId().equals(3L)).toList();
        });
    }

    @AfterEach
    void clearContext() {
        DataPermissionContextHolder.clear();
        TenantContextHolder.clear();
    }

    @Test
    void validatesAnActiveReviewerWithoutWideningSubsequentUserQueries() {
        assertUserMissing(() -> service.validateUserList(List.of(2L)));
        assertDoesNotThrow(() -> api.validateUser(2L));
        assertUserMissing(() -> service.validateUserList(List.of(2L)));
        assertNull(DataPermissionContextHolder.get());
    }

    @Test
    void rejectsUnknownUsersAndRestoresScopeAfterFailure() {
        assertUserMissing(() -> api.validateUser(999L));
        assertNull(DataPermissionContextHolder.get());
        assertUserMissing(() -> service.validateUserList(List.of(2L)));
    }

    @Test
    void rejectsDisabledReviewersEvenOutsideTheCallersListScope() {
        ServiceException failure = assertThrows(ServiceException.class, () -> api.validateUser(4L));
        assertEquals(USER_IS_DISABLE.getCode(), failure.getCode());
        assertNull(DataPermissionContextHolder.get());
    }

    @Test
    void keepsTenantIsolationWhenValidatingAnExplicitIdentity() {
        assertUserMissing(() -> api.validateUser(5L));
        assertEquals(1L, TenantContextHolder.getTenantId());
        assertFalse(TenantContextHolder.isIgnore());
    }

    @Test
    void restoresAnExistingPermissionContextOnSuccessAndFailure() throws Exception {
        DataPermission outer = getClass().getDeclaredMethod("restrictedScope").getAnnotation(DataPermission.class);
        DataPermissionContextHolder.add(outer);
        assertDoesNotThrow(() -> api.validateUserList(List.of(2L, 3L)));
        assertSame(outer, DataPermissionContextHolder.get());
        assertUserMissing(() -> api.validateUser(999L));
        assertSame(outer, DataPermissionContextHolder.get());
    }

    @DataPermission
    private void restrictedScope() { }

    private static AdminUserDO user(Long id, Long tenantId, int status) {
        AdminUserDO user = new AdminUserDO().setId(id).setNickname("Account " + id).setStatus(status);
        user.setTenantId(tenantId);
        return user;
    }

    private static void assertUserMissing(org.junit.jupiter.api.function.Executable action) {
        ServiceException failure = assertThrows(ServiceException.class, action);
        assertEquals(USER_NOT_EXISTS.getCode(), failure.getCode());
    }
}
