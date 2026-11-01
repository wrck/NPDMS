package cn.iocoder.yudao.module.system.service.auth;

import cn.hutool.core.lang.Validator;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.controller.admin.auth.vo.AuthEmailResetPasswordReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.framework.sms.config.SmsCodeProperties;
import cn.iocoder.yudao.module.system.service.user.AdminUserService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/** Password recovery is separate from ordinary mail logging: no code or mail body is persisted. */
@Service
public class EmailPasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final DefaultRedisScript<Long> ATTEMPT = new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); if not v then return 0 end; " +
            "local n=redis.call('INCR',KEYS[2]); if n==1 then redis.call('PEXPIRE',KEYS[2],redis.call('PTTL',KEYS[1])) end; " +
            "if n>5 then redis.call('DEL',KEYS[1],KEYS[2]); return 0 end; return n", Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>(
            "if redis.call('GET',KEYS[1])==ARGV[1] then redis.call('DEL',KEYS[1],KEYS[2]); return 1 end; return 0", Long.class);
    @Resource private AdminUserService users;
    @Resource private StringRedisTemplate redis;
    @Resource private SmsCodeProperties policy;
    @Value("${pms.password-reset.mail.host:}") private String host;
    @Value("${pms.password-reset.mail.port:587}") private int port;
    @Value("${pms.password-reset.mail.username:}") private String username;
    @Value("${pms.password-reset.mail.password:}") private String password;
    @Value("${pms.password-reset.mail.from:}") private String from;

    public void send(String account) {
        if (host.isBlank() || username.isBlank() || password.isBlank() || from.isBlank()) throw failure("邮件服务未配置");
        AdminUserDO user = users.getUserByUsername(account);
        if (user == null || CommonStatusEnum.isDisable(user.getStatus()) || !Validator.isEmail(user.getEmail())) return;
        String base = base(user);
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(base + ":cooldown", "1", policy.getSendFrequency())))
            return;
        String daily = base + ":daily:" + LocalDate.now();
        Long count = redis.opsForValue().increment(daily);
        if (Objects.equals(count, 1L)) redis.expire(daily, Duration.ofDays(2));
        if (count == null || count > policy.getSendMaximumQuantityPerDay()) return;
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        String key = base + ":code";
        redis.opsForValue().set(key, ENCODER.encode(code + "|" + user.getEmail()), policy.getExpireTimes());
        redis.delete(base + ":attempts");
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(host); sender.setPort(port); sender.setUsername(username); sender.setPassword(password);
            Properties properties = sender.getJavaMailProperties();
            properties.put("mail.smtp.auth", "true"); properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
            properties.put("mail.smtp.ssl.checkserveridentity", "true");
            properties.put("mail.smtp.connectiontimeout", "5000"); properties.put("mail.smtp.timeout", "5000");
            properties.put("mail.smtp.writetimeout", "5000");
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from); message.setTo(user.getEmail()); message.setSubject("密码重置验证码");
            message.setText("您的密码重置验证码为：" + code + "。请勿向他人透露。");
            sender.send(message);
        } catch (RuntimeException ex) {
            redis.delete(key);
            throw failure("验证码发送失败，请稍后重试");
        }
    }

    public void reset(AuthEmailResetPasswordReqVO request) {
        AdminUserDO user = users.getUserByUsername(request.getUsername());
        if (user == null || CommonStatusEnum.isDisable(user.getStatus()) || !Validator.isEmail(user.getEmail())) throw invalid();
        String base = base(user), key = base + ":code", attempts = base + ":attempts";
        String hash = redis.opsForValue().get(key);
        if (hash == null) throw invalid();
        Long n = redis.execute(ATTEMPT, List.of(key, attempts));
        if (n == null || n == 0) throw invalid();
        if (!ENCODER.matches(request.getCode() + "|" + user.getEmail(), hash)) {
            if (n == 5) redis.execute(CONSUME, List.of(key, attempts), hash);
            throw invalid();
        }
        if (!Objects.equals(1L, redis.execute(CONSUME, List.of(key, attempts), hash))) throw invalid();
        users.updateUserPassword(user.getId(), request.getPassword());
    }

    private String base(AdminUserDO user) { return "auth:email-reset:" + TenantContextHolder.getRequiredTenantId() + ":" + user.getId(); }
    private ServiceException invalid() { return failure("验证码无效或已过期"); }
    private ServiceException failure(String message) { return new ServiceException(400, message); }
}
