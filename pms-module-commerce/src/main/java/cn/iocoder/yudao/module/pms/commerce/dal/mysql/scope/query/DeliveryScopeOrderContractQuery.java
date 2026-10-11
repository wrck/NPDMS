package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.util.List;

/** Contracts for the already authorized delivery-scope page, resolved within COM. */
public record DeliveryScopeOrderContractQuery(Long tenantId, List<Long> scopeIds) {
}
