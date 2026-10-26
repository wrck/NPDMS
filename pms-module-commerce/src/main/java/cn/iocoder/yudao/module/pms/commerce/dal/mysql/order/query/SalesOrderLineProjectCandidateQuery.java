package cn.iocoder.yudao.module.pms.commerce.dal.mysql.order.query;

/** Candidate projects for a specified order; never an authorization result. */
public record SalesOrderLineProjectCandidateQuery(Long tenantId, Long orderId, String orderNo) {
}
