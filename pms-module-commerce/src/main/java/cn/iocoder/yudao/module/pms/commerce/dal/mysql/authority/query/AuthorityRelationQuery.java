package cn.iocoder.yudao.module.pms.commerce.dal.mysql.authority.query;

public record AuthorityRelationQuery(Long tenantId, String sourceSystem,
                                     String salesOrderSourceKey, String contractSourceKey) {
}
