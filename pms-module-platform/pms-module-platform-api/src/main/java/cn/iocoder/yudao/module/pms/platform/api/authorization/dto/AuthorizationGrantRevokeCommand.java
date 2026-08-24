package cn.iocoder.yudao.module.pms.platform.api.authorization.dto;

public record AuthorizationGrantRevokeCommand(
        Long tenantId,
        Long actorId,
        Long grantId,
        Integer expectedVersion,
        String reason,
        String idempotencyKey,
        String requestDigest) {
}
