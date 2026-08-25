package cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query;

import java.util.Set;

/** 割接治理守卫任务查询。 */
public record CutoverGovernanceGuardQuery(Long tenantId, Set<Long> projectIds) {

    public CutoverGovernanceGuardQuery {
        if (tenantId == null || projectIds == null) {
            throw new IllegalArgumentException("tenantId and projectIds must not be null");
        }
        projectIds = Set.copyOf(projectIds);
    }
}
