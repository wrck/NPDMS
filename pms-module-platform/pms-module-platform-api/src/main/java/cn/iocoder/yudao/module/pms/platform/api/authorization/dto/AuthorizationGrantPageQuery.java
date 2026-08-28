package cn.iocoder.yudao.module.pms.platform.api.authorization.dto;

import java.time.LocalDateTime;

public record AuthorizationGrantPageQuery(
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
        Integer pageNo,
        Integer pageSize) {
}
