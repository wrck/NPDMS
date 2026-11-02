package cn.iocoder.yudao.module.system.service.auth;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthEmailResetPasswordReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.framework.sms.config.SmsCodeProperties;
import cn.iocoder.yudao.module.system.service.mail.PasswordResetMailService;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailPasswordResetServiceTest {
    private EmailPasswordResetService service;
    private AdminUserService users;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private AuthEmailResetPasswordReqVO request;

    @SuppressWarnings("unchecked")
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(1L);
        service = new EmailPasswordResetService();
        users = mock(AdminUserService.class); redis = mock(StringRedisTemplate.class); values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        ReflectionTestUtils.setField(service, "users", users);
        ReflectionTestUtils.setField(service, "redis", redis);
        when(users.getUserByUsername("alice")).thenReturn(new AdminUserDO().setId(7L).setStatus(0).setEmail("test@example.invalid"));
        request = new AuthEmailResetPasswordReqVO(); request.setUsername("alice"); request.setCode("123456"); request.setPassword("newpass");
    }
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    @Test void expiredCodeCannotChangePassword() {
        assertThrows(ServiceException.class, () -> service.reset(request));
        verify(users, never()).updateUserPassword(anyLong(), anyString());
    }
    @Test void wrongFifthCodeConsumesChallenge() {
        when(values.get(anyString())).thenReturn(new BCryptPasswordEncoder().encode("654321|test@example.invalid"));
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(5L);
        assertThrows(ServiceException.class, () -> service.reset(request));
        verify(redis).execute(any(RedisScript.class), anyList(), anyString());
        verify(users, never()).updateUserPassword(anyLong(), anyString());
    }
    @Test void validCodeMustBeConsumedBeforePasswordUpdate() {
        when(values.get(anyString())).thenReturn(new BCryptPasswordEncoder().encode("123456|test@example.invalid"));
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(0L);
        assertThrows(ServiceException.class, () -> service.reset(request));
        verify(users, never()).updateUserPassword(anyLong(), anyString());
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        service.reset(request);
        verify(users).updateUserPassword(7L, "newpass");
    }
    @Test void unboundOrDisabledAccountCannotReset() {
        when(users.getUserByUsername("alice")).thenReturn(new AdminUserDO().setId(7L).setStatus(1).setEmail("test@example.invalid"));
        assertThrows(ServiceException.class, () -> service.reset(request));
        verifyNoInteractions(redis);
    }
    @Test void mailFailureInvalidatesTheNewChallenge() {
        var policy = new SmsCodeProperties();
        policy.setSendFrequency(Duration.ofMinutes(1)); policy.setExpireTimes(Duration.ofMinutes(10));
        policy.setSendMaximumQuantityPerDay(10);
        var mail = mock(PasswordResetMailService.class);
        ReflectionTestUtils.setField(service, "policy", policy); ReflectionTestUtils.setField(service, "mail", mail);
        when(values.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(values.increment(anyString())).thenReturn(1L);
        doThrow(new IllegalStateException("failure")).when(mail).send(any());
        assertThrows(ServiceException.class, () -> service.send("alice"));
        verify(redis).delete("auth:email-reset:1:7:code");
    }
}
