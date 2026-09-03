package cn.iocoder.yudao.module.pms.platform.controller.admin.collection.vo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceCredentialCreateReqVO {

    @NotBlank
    private String credentialCode;
    @NotBlank
    private String credentialType;
    @NotBlank
    private String username;
    private char[] secret;
    private String kmsReference;
    @NotBlank
    private String deviceId;
    @NotBlank
    private String commandTemplateId;
    private LocalDateTime expiresAt;

    @AssertTrue(message = "凭证必须且只能提供秘密或KMS引用")
    public boolean isSecretSourceValid() {
        boolean hasSecret = secret != null && secret.length > 0;
        boolean hasKmsReference = kmsReference != null && !kmsReference.isBlank();
        return hasSecret != hasKmsReference;
    }
}
