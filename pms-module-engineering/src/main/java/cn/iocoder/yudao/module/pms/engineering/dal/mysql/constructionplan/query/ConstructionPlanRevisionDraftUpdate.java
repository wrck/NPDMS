package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

import java.time.LocalDate;

/** 未冻结候选工期版本的场景化CAS更新。 */
public record ConstructionPlanRevisionDraftUpdate(
        Long tenantId, Long planId, Long revisionId, Integer expectedVersion,
        String calculationBasisCode, LocalDate startDate, LocalDate endDate,
        Integer durationDays, Long sourceChangeId) {
}
