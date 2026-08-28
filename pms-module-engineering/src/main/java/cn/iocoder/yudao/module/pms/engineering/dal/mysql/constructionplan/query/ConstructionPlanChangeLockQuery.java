package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

/** 工期变更锁定查询。 */
public record ConstructionPlanChangeLockQuery(Long tenantId, Long planId, Long changeId) {
}
