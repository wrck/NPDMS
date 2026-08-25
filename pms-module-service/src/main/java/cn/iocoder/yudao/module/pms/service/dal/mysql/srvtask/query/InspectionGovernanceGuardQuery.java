package cn.iocoder.yudao.module.pms.service.dal.mysql.srvtask.query;

import java.util.Set;

/** 巡检治理守卫任务查询。 */
public record InspectionGovernanceGuardQuery(Long tenantId, Set<Long> projectIds) {

    public InspectionGovernanceGuardQuery {
        if (tenantId == null || projectIds == null) {
            throw new IllegalArgumentException("tenantId and projectIds must not be null");
        }
        projectIds = Set.copyOf(projectIds);
    }
}
