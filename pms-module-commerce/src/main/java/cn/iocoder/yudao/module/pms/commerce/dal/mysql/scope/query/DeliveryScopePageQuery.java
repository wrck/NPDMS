package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.util.List;

public record DeliveryScopePageQuery(
        Long tenantId,
        List<Long> projectIds,
        Long projectId,
        Long orderLineId,
        boolean includeHistory,
        int offset,
        int limit) {
}
