package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

import java.util.List;

public record SalesOrderDetailScopeQuery(Long tenantId, Long orderId, List<String> companyCodes,
                                        List<Long> projectIds) {
}
