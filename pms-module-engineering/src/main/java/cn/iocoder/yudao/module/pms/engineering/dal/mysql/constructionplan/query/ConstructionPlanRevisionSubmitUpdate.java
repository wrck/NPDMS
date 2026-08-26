package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

import java.time.LocalDateTime;

/** 提交审批时冻结候选工期版本。 */
public record ConstructionPlanRevisionSubmitUpdate(
        Long tenantId, Long planId, Long revisionId, Integer expectedVersion,
        Long sourceChangeId, LocalDateTime frozenAt) {
}
