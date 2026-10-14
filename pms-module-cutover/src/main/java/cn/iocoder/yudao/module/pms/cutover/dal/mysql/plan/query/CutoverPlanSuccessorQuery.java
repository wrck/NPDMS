package cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query;

public record CutoverPlanSuccessorQuery(Long tenantId, Long taskId, Long sourcePlanRevisionId) {
}
