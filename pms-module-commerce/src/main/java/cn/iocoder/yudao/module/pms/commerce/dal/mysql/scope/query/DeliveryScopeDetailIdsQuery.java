package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.util.List;

public record DeliveryScopeDetailIdsQuery(Long tenantId, List<Long> deliveryScopeIds) {
}
