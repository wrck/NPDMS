package cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query;

import java.time.LocalDateTime;

public record AuthorizationGrantPageCriteria(
        Long tenantId,
        String subjectTypeCode,
        Long subjectId,
        String resourceContextCode,
        String resourceTypeCode,
        Long resourceId,
        String actionCode,
        String scopeCode,
        String statusCode,
        LocalDateTime effectiveAt,
        long offset,
        int limit) {
}
