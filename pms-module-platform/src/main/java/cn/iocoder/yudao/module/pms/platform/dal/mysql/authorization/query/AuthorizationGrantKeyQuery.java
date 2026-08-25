package cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query;

import java.time.LocalDateTime;

public record AuthorizationGrantKeyQuery(
        Long tenantId,
        String subjectTypeCode,
        Long subjectId,
        String resourceContextCode,
        String resourceTypeCode,
        Long resourceId,
        String actionCode,
        String scopeCode,
        LocalDateTime expiredAt) {
}
