package cn.iocoder.yudao.module.pms.platform.dal.mysql.authorization.query;

import java.time.LocalDateTime;
import java.util.Set;

public record EffectiveAuthorizationGrantQuery(
        Long tenantId,
        String subjectTypeCode,
        Long subjectId,
        String resourceContextCode,
        String resourceTypeCode,
        Set<Long> resourceIds,
        String actionCode,
        LocalDateTime effectiveAt) {
}
