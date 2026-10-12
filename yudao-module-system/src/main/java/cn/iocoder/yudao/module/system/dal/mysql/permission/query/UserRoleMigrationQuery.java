package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

import java.util.Set;

/** User and role existence checks are confined to the migration's current tenant. */
public record UserRoleMigrationQuery(Long tenantId, Set<Long> userIds, Set<Long> roleIds) {}
