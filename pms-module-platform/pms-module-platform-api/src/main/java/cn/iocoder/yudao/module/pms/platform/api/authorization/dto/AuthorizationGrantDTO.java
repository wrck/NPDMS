package cn.iocoder.yudao.module.pms.platform.api.authorization.dto;

import java.time.LocalDateTime;

public record AuthorizationGrantDTO(
        Long id,
        Long tenantId,
        String subjectTypeCode,
        Long subjectId,
        String resourceContextCode,
        String resourceTypeCode,
        Long resourceId,
        String actionCode,
        String scopeCode,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        String statusCode,
        String sourceContextCode,
        String sourceObjectType,
        String sourceObjectId,
        Long grantedBy,
        LocalDateTime grantedAt,
        Long revokedBy,
        LocalDateTime revokedAt,
        String revokeReason,
        Integer version) {
}
