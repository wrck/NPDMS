package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

import java.util.List;

public record SalesOrderCompanyScopeQuery(
        Long tenantId, List<String> companyCodes, String orderNo, String status, int offset, int limit) {
}
