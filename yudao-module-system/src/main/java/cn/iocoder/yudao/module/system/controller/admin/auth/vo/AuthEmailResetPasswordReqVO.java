package cn.iocoder.yudao.module.system.controller.admin.auth.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.ToString;
import org.hibernate.validator.constraints.Length;

/** Reset an account password using its email verification code. */
@Data
public class AuthEmailResetPasswordReqVO {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "邮箱验证码不能为空")
    @Pattern(regexp = "[0-9]{6}", message = "请输入六位邮箱验证码")
    @ToString.Exclude
    private String code;

    @NotBlank(message = "密码不能为空")
    @Length(min = 4, max = 16, message = "密码长度为 4-16 位")
    @ToString.Exclude
    private String password;
}
