package cn.iocoder.yudao.module.pms.commerce.dal.mysql.scope.query;

import java.util.List;

public record DeliveryScopeOrderLineQuery(Long tenantId, List<Long> orderLineIds) {
}
