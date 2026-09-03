package cn.iocoder.yudao.module.pms.cutover.dal.mysql.dashboard.query;

import java.util.Set;

/** Stable cursor query for CUT dashboard candidates. */
public record CutoverDashboardCandidateQuery(Long tenantId, Set<Long> visibleProjectIds,
                                              Long afterTaskId, int limit) {
    public CutoverDashboardCandidateQuery {
        if (tenantId == null || tenantId <= 0 || visibleProjectIds == null
                || afterTaskId == null || afterTaskId < 0 || limit <= 0 || limit > 500) {
            throw new IllegalArgumentException("dashboard candidate query is invalid");
        }
        visibleProjectIds = Set.copyOf(visibleProjectIds);
    }
}
