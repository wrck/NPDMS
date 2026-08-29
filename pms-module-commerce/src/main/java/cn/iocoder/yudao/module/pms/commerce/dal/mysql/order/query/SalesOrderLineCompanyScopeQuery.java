package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

import java.util.List;

public record SalesOrderLineCompanyScopeQuery(
        Long tenantId, List<String> companyCodes, List<Long> projectIds, Long orderId,
        String companyCode, String orderType, String orderNo, String lineNo,
        String itemCode, String productCode, String quantityStatus, String status,
        int offset, int limit) {
}
