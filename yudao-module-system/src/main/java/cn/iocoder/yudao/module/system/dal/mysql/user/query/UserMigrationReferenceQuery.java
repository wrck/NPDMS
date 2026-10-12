package cn.iocoder.yudao.module.system.dal.mysql.user.query;

import java.util.Set;
public record UserMigrationReferenceQuery(Long tenantId, Set<Long> ids) {}
