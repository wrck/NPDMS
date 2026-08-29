package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

import java.util.List;

public record SalesOrderCompanyScopeQuery(
        Long tenantId, List<String> companyCodes, List<Long> projectIds, String companyCode,
        String orderNo, String orderType, String customerKeyword, String status, int offset, int limit) {
}
