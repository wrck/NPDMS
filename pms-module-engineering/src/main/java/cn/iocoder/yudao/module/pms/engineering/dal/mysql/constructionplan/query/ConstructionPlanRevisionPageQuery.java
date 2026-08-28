package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

/** 工期版本稳定游标页查询，按revisionNo/id倒序。 */
public record ConstructionPlanRevisionPageQuery(
        Long tenantId,
        Long planId,
        Integer cursorRevisionNo,
        Long cursorId,
        Integer limit) {
}
