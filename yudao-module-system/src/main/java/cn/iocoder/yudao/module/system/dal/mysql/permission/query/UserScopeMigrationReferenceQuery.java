package cn.iocoder.yudao.module.system.dal.mysql.permission.query;

import java.util.Set;

public record UserScopeMigrationReferenceQuery(Long tenantId, Set<Long> userIds,
                                               Set<Long> companyIds, Set<Long> departmentIds,
                                               Set<String> roleCodes) {}
