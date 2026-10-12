package cn.iocoder.yudao.module.system.service.auth;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAuthMigratedAccountStatusTest {
    private AdminAuthServiceImpl service;
    private AdminUserService users;
    private OAuth2TokenService tokens;
    private LoginLogService logs;
    private SmsCodeApi sms;
    private final AuthSmsLoginReqVO request = new AuthSmsLoginReqVO("13800000000", "1024");

    @BeforeEach void setup() {
        service = new AdminAuthServiceImpl(); users = mock(AdminUserService.class);
        tokens = mock(OAuth2TokenService.class); logs = mock(LoginLogService.class); sms = mock(SmsCodeApi.class);
        ReflectionTestUtils.setField(service, "userService", users);
        ReflectionTestUtils.setField(service, "oauth2TokenService", tokens);
        ReflectionTestUtils.setField(service, "loginLogService", logs);
        ReflectionTestUtils.setField(service, "smsCodeApi", sms);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }
    @AfterEach void close() { RequestContextHolder.resetRequestAttributes(); }

    @Test void disabledHistoricalAccountCannotReceiveSmsLoginTokenOrSuccessfulLoginTime() {
        when(users.getUserByMobile(request.getMobile())).thenReturn(new AdminUserDO().setId(9L).setStatus(1));
        assertEquals(AUTH_LOGIN_USER_DISABLED.getCode(), assertThrows(ServiceException.class, () -> service.smsLogin(request)).getCode());
        verify(sms).useSmsCode(any()); verifyNoInteractions(tokens);
        verify(users, never()).updateUserLogin(anyLong(), any());
        var evidence = ArgumentCaptor.forClass(LoginLogCreateReqDTO.class);
        verify(logs).createLoginLog(evidence.capture());
        assertEquals(LoginResultEnum.USER_DISABLED.getResult(), evidence.getValue().getResult());
        assertEquals(9L, evidence.getValue().getUserId());
    }

    @Test void enabledUniqueAccountContinuesThroughExistingSmsAndTokenFlow() {
        when(users.getUserByMobile(request.getMobile())).thenReturn(new AdminUserDO().setId(9L).setStatus(0));
        when(tokens.createAccessToken(9L, 2, OAuth2ClientConstants.CLIENT_ID_DEFAULT, null)).thenReturn(new OAuth2AccessTokenDO().setUserId(9L));
        assertNotNull(service.smsLogin(request));
        verify(sms).useSmsCode(any());
        verify(tokens).createAccessToken(9L, 2, OAuth2ClientConstants.CLIENT_ID_DEFAULT, null);
        verify(users).updateUserLogin(eq(9L), any());
    }
}
