package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query;

public record CutoverPlanVersionUpdate(Long tenantId, Long planRevisionId, Long expectedVersion,
                                       Long newVersion) {
}
