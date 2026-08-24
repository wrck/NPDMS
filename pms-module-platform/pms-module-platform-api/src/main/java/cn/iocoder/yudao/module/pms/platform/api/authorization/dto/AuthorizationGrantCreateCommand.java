package cn.iocoder.yudao.module.pms.platform.api.authorization.dto;

import java.time.LocalDateTime;

public record AuthorizationGrantCreateCommand(
        Long tenantId,
        Long actorId,
        String idempotencyKey,
        String requestDigest,
        String subjectTypeCode,
        Long subjectId,
        String resourceContextCode,
        String resourceTypeCode,
        Long resourceId,
        String actionCode,
        String scopeCode,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        String sourceContextCode,
        String sourceObjectType,
        String sourceObjectId,
        String reason) {
}
