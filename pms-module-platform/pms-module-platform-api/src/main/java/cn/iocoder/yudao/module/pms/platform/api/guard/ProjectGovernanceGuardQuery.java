package cn.iocoder.yudao.module.pms.platform.api.guard;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/** 项目治理提供方只读批量查询。 */
public record ProjectGovernanceGuardQuery(
        Long tenantId,
        Set<Long> projectIds,
        String action,
        LocalDateTime checkedAt) {

    public ProjectGovernanceGuardQuery {
        if (tenantId == null || projectIds == null || action == null || action.isBlank()
                || checkedAt == null || projectIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("invalid project governance guard query");
        }
        projectIds = Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(projectIds)));
        action = action.trim();
    }
}
