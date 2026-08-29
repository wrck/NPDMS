package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

import java.util.List;

public record SalesOrderLineCompanyScopeQuery(
        Long tenantId, List<String> companyCodes, Long orderId, String lineNo, int offset, int limit) {
}
