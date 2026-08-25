package cn.iocoder.yudao.module.pms.platform.api.authorization.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record AuthorizationGrantQuery(
        Long tenantId,
        String subjectTypeCode,
        Long subjectId,
        String resourceContextCode,
        String resourceTypeCode,
        Set<Long> resourceIds,
        String actionCode,
        LocalDateTime effectiveAt) {
}
