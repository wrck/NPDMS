package cn.iocoder.yudao.module.pms.project.dal.mysql.acceptancereport.query;

import java.util.Set;

public record AcceptanceActivityScopeQuery(Long tenantId, Set<Long> projectIds) {

    public AcceptanceActivityScopeQuery {
        if (tenantId == null || projectIds == null || projectIds.isEmpty()) {
            throw new IllegalArgumentException("tenantId and projectIds are required");
        }
        projectIds = Set.copyOf(projectIds);
    }
}
