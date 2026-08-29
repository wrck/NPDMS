package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

import java.util.List;

public record SalesOrderLineIdsQuery(Long tenantId, List<Long> orderLineIds) {
}
