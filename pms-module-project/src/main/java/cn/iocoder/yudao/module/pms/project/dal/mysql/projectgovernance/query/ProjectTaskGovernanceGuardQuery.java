package cn.iocoder.yudao.module.pms.project.dal.mysql.projectgovernance.query;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/** 项目任务治理守卫查询。 */
public record ProjectTaskGovernanceGuardQuery(Long tenantId, Set<Long> projectIds) {

    public ProjectTaskGovernanceGuardQuery {
        if (tenantId == null || projectIds == null
                || projectIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("invalid project task governance guard query");
        }
        projectIds = Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(projectIds)));
    }
}
