package cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query;

import java.time.LocalDateTime;

public record AuthorizationGrantRevokeUpdate(
        Long tenantId,
        Long grantId,
        Integer expectedVersion,
        Long revokedBy,
        LocalDateTime revokedAt,
        String revokeReason) {
}
