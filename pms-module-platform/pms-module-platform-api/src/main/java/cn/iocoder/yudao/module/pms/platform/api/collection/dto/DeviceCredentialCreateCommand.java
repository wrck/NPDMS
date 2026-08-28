package cn.iocoder.yudao.module.pms.platform.api.collection.dto;

import java.time.LocalDateTime;

public record DeviceCredentialCreateCommand(
        Long tenantId,
        Long actorId,
        String credentialCode,
        String credentialType,
        String username,
        char[] secret,
        String kmsReference,
        String deviceId,
        String commandTemplateId,
        LocalDateTime expiresAt) {
}
