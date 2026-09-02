package cn.iocoder.yudao.module.pms.project.dal.mysql.satisfaction.query;

import java.util.Set;

public record SatisfactionTaskScopeQuery(Long tenantId, Set<Long> visibleProjectIds, Long assignedToUserId) {
}
