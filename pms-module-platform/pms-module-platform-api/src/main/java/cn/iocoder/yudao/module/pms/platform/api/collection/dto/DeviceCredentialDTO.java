package cn.iocoder.yudao.module.pms.platform.api.collection.dto;

public record DeviceCredentialDTO(
        Long id,
        String credentialCode,
        String credentialType,
        String username,
        String secretMask,
        Long credentialVersion,
        String status,
        Long defaultGrantId) {
}
