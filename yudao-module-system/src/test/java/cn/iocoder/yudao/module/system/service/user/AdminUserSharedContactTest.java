package cn.iocoder.yudao.module.system.service.user;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.UserEmailLookupQuery;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.UserMobileLookupQuery;
import cn.iocoder.yudao.module.system.mq.producer.user.AdminUserProducer;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserSharedContactTest {
    private AdminUserMapper users;
    private AdminUserServiceImpl service;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L); users = mock(AdminUserMapper.class); service = new AdminUserServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "adminUserProducer", mock(AdminUserProducer.class));
    }
    @AfterEach void close() { TenantContextHolder.clear(); }
    private AdminUserDO user(long id) { return new AdminUserDO().setId(id).setUsername("account"+id).setNickname("保留姓名")
            .setEmail("shared@example.invalid").setMobile("13800000000").setStatus(0); }
    private void code(int expected, Runnable operation) { assertEquals(expected, assertThrows(ServiceException.class, operation::run).getCode()); }
    @Test void everydayNewOrChangedContactStillRequiresUniqueness() {
        when(users.selectEmailAccounts(new UserEmailLookupQuery(1L,"shared@example.invalid"))).thenReturn(List.of(user(9),user(10)));
        when(users.selectMobileLoginCandidates(new UserMobileLookupQuery(1L,"13800000000"))).thenReturn(List.of(user(9),user(10)));
        code(USER_EMAIL_EXISTS.getCode(), () -> service.validateEmailUnique(null,"shared@example.invalid"));
        code(USER_EMAIL_EXISTS.getCode(), () -> service.validateEmailUnique(9L,"shared@example.invalid"));
        code(USER_MOBILE_EXISTS.getCode(), () -> service.validateMobileUnique(null,"13800000000"));
        code(USER_MOBILE_EXISTS.getCode(), () -> service.validateMobileUnique(9L,"13800000000"));
    }
    @Test void unchangedMigratedContactsDoNotPreventUnrelatedProfileMaintenance() {
        when(users.selectById(9L)).thenReturn(user(9));
        var request = new UserProfileUpdateReqVO(); request.setNickname("保留姓名");
        request.setEmail("shared@example.invalid"); request.setMobile("13800000000");
        assertDoesNotThrow(() -> service.updateUserProfile(9L,request));
        verify(users).updateById(any(AdminUserDO.class));
        verify(users,never()).selectEmailAccounts(any()); verify(users,never()).selectMobileLoginCandidates(any());
    }
    @Test void changedProfileContactsCannotIntroduceNewSharedValues() {
        when(users.selectById(9L)).thenReturn(user(9).setEmail("previous@example.invalid"));
        when(users.selectEmailAccounts(new UserEmailLookupQuery(1L,"shared@example.invalid"))).thenReturn(List.of(user(10)));
        var request = new UserProfileUpdateReqVO(); request.setEmail("shared@example.invalid"); request.setMobile("13800000000");
        code(USER_EMAIL_EXISTS.getCode(), () -> service.updateUserProfile(9L,request));
        verify(users,never()).updateById(any(AdminUserDO.class));
    }
    @Test void ambiguousSmsIdentityNeverSelectsTheFirstAccount() {
        when(users.selectMobileLoginCandidates(new UserMobileLookupQuery(1L,"13800000000"))).thenReturn(List.of(user(9),user(10)));
        code(USER_MOBILE_AMBIGUOUS.getCode(), () -> service.getUserByMobile("13800000000"));
    }
    @Test void uniqueMobileResolvesWithinExplicitTenantAndNoMatchReturnsNull() {
        when(users.selectMobileLoginCandidates(new UserMobileLookupQuery(1L,"13800000000"))).thenReturn(List.of(user(9)));
        assertEquals(9L,service.getUserByMobile("13800000000").getId());
        assertNull(service.getUserByMobile("13900000000"));
        TenantContextHolder.setTenantId(2L); assertNull(service.getUserByMobile("13800000000"));
    }
}
