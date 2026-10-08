package cn.iocoder.yudao.module.pms.platform.support.business;

import java.util.Set;

/** Service-resolved tenant/project scope, never bound directly from an HTTP body. */
public record BusinessReadQuery(Long tenantId, Set<Long> projectIds, BusinessPageQuery criteria) {
    public BusinessReadQuery { projectIds = Set.copyOf(projectIds); }
}
