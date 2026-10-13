package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.util.List;

/** Only IDs already returned by the authorized scope page may enter this query. */
public record DeliveryScopeOrderFieldsQuery(Long tenantId, List<Long> scopeIds) {}
