package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

import java.util.List;

/** 订单行级实际执行单号收集（仅作补充展示，不参与主取值链）。 */
public record OrderLineExecutionNoQuery(Long tenantId, List<Long> orderIds) {
}
