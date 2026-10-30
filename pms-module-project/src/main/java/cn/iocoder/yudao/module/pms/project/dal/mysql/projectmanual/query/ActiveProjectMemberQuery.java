package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import java.time.LocalDateTime;
import java.util.Set;

public record ActiveProjectMemberQuery(Long tenantId, Long userId, LocalDateTime effectiveAt, Set<Long> projectIds) {
    public ActiveProjectMemberQuery(Long tenantId, Long userId, LocalDateTime effectiveAt) {
        this(tenantId, userId, effectiveAt, null);
    }
    public ActiveProjectMemberQuery {
        projectIds = projectIds == null ? null : Set.copyOf(projectIds);
    }
}
