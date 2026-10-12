package cn.iocoder.yudao.module.system.dal.mysql.user.query;

/** A phone number is a contact fact, not an account identity or stable unique key. */
public record UserMobileLookupQuery(Long tenantId, String mobile) {}
