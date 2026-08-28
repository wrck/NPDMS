package cn.iocoder.yudao.module.pms.engineering.dal.mysql.constructionplan.query;

/** 文件业务Provider按受信租户和稳定变更ID定位SOL对象。 */
public record ConstructionPlanChangeObjectQuery(Long tenantId, Long changeId) {
}
