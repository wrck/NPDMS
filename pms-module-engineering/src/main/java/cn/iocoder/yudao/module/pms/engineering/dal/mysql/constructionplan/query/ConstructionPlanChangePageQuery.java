package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

import java.time.LocalDateTime;

/** 工期变更稳定游标页查询，按createdAt/id倒序。 */
public record ConstructionPlanChangePageQuery(
        Long tenantId,
        Long planId,
        LocalDateTime cursorCreatedAt,
        Long cursorId,
        Integer limit) {
}
