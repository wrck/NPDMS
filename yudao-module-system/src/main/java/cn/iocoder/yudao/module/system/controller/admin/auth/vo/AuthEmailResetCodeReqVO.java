package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Request a password reset code for the account's bound email address. */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthEmailResetCodeReqVO extends CaptchaVerificationReqVO {

    @NotBlank(message = "账号不能为空")
    private String username;
}
