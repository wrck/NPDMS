package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

/** 工期版本锁定查询。 */
public record ConstructionPlanRevisionLockQuery(Long tenantId, Long planId, Long revisionId) {
}
