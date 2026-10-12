package cn.iocoder.yudao.module.system.dal.mysql.dept.query;

import java.util.Set;

public record DepartmentMigrationLeaderQuery(Long tenantId,Set<Long> userIds) {}
