package cn.iocoder.yudao.module.system.dal.mysql.user.query;

/** Existing shared historical email values require a collection lookup, not selectOne. */
public record UserEmailLookupQuery(Long tenantId, String email) {}
