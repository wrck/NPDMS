package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

import java.util.List;

/** Bounded keyset read of currently authorized order roots, in native directory order. */
public record SalesOrderRootReadPageQuery(Long tenantId, List<String> companyCodes, List<Long> projectIds,
                                         String afterCode, Long afterId, int limit) {
}
