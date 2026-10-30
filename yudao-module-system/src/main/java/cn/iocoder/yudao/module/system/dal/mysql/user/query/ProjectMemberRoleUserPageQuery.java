package cn.iocoder.yudao.module.system.dal.mysql.user.query;

import java.util.Set;

/** 当前租户生效系统角色用户；部门关键字匹配ID由普通部门数据权限产生。 */
public record ProjectMemberRoleUserPageQuery(Long tenantId, Set<String> roleCodes, String keyword,
                                           int pageNo, int pageSize, Set<Long> matchedDepartmentIds) {
    public long offset() { return (pageNo - 1L) * pageSize; }
}
