package cn.iocoder.yudao.module.system.service.mail;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailAccountDO;
import cn.iocoder.yudao.module.system.dal.dataobject.mail.MailTemplateDO;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Sends a verification message without persisting its secret in mail logs or queues. */
@Service
public class PasswordResetMailService {
    public static final String TEMPLATE_CODE = "admin-password-reset";
    public record Request(Long userId, String recipient, String code) {
        @Override public String toString() { return "PasswordResetMailRequest[userId=" + userId + "]"; }
    }
    @Resource private MailTemplateService templates;
    @Resource private MailAccountService accounts;
    @Resource private MailLogService logs;

    public void send(Request request) {
        MailTemplateDO template = templates.getMailTemplateByCodeFromCache(TEMPLATE_CODE);
        if (template == null || template.getParams() == null || !template.getParams().contains("code")
                || template.getParams().stream().anyMatch(p -> !"code".equals(p)))
            throw new ServiceException(400, "密码重置邮件模板未配置或未启用");
        MailAccountDO account = accounts.getMailAccountForSending(template.getAccountId());
        if (account == null || account.getPassword() == null || account.getPassword().isBlank())
            throw new ServiceException(400, "密码重置邮件账号未配置");
        Map<String, Object> redacted = Map.of("code", "[已隐藏]");
        String safeContent = templates.formatMailTemplateContent(template.getContent(), redacted);
        boolean enabled = CommonStatusEnum.ENABLE.getStatus().equals(template.getStatus());
        Long logId = logs.createMailLog(request.userId(), UserTypeEnum.ADMIN.getValue(),
                List.of(request.recipient()), List.of(), List.of(), account, template, safeContent, redacted, enabled);
        if (!enabled) throw new ServiceException(400, "密码重置邮件模板未启用");
        try {
            Map<String, Object> params = Map.of("code", request.code());
            deliver(account, template.getNickname(), templates.formatMailTemplateContent(template.getTitle(), params),
                    templates.formatMailTemplateContent(template.getContent(), params), request.recipient());
            logs.updateMailSendResult(logId, null, null);
        } catch (Exception ex) {
            logs.updateMailSendResult(logId, null, new IllegalStateException("密码重置邮件发送失败"));
            throw new ServiceException(400, "验证码发送失败，请稍后重试");
        }
    }

    protected void deliver(MailAccountDO account, String nickname, String title, String content, String recipient) throws Exception {
        if (!Boolean.TRUE.equals(account.getSslEnable()) && !Boolean.TRUE.equals(account.getStarttlsEnable()))
            throw new IllegalStateException("SMTP transport must be encrypted");
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getHost()); sender.setPort(account.getPort());
        sender.setUsername(account.getUsername()); sender.setPassword(account.getPassword());
        Properties p = sender.getJavaMailProperties();
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.ssl.enable", Boolean.toString(Boolean.TRUE.equals(account.getSslEnable())));
        p.put("mail.smtp.starttls.enable", Boolean.toString(Boolean.TRUE.equals(account.getStarttlsEnable())));
        p.put("mail.smtp.starttls.required", Boolean.toString(Boolean.TRUE.equals(account.getStarttlsEnable())));
        p.put("mail.smtp.ssl.checkserveridentity", "true");
        p.put("mail.smtp.connectiontimeout", "5000"); p.put("mail.smtp.timeout", "5000"); p.put("mail.smtp.writetimeout", "5000");
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
        if (nickname == null || nickname.isBlank()) helper.setFrom(account.getMail());
        else helper.setFrom(account.getMail(), nickname);
        helper.setTo(recipient); helper.setSubject(title); helper.setText(content, true);
        sender.send(message);
    }
}
