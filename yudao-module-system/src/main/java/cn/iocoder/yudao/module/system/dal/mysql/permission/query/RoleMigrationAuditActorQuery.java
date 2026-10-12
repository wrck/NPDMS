package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

import java.util.Set;

/** Audit identity validation accepts disabled historical users, but never another tenant. */
public record RoleMigrationAuditActorQuery(Long tenantId, Set<Long> userIds) {}
