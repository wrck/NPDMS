package cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query;

public record CutoverPlanVersionUpdate(Long tenantId, Long planRevisionId, Integer expectedVersion,
                                       Integer newVersion) {
}
