package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectViewReadScopeApi;
import java.time.LocalDateTime;
import java.util.Set;

public record ProjectMemberFilterUsersQuery(Long tenantId, Set<String> roleCodes, LocalDateTime effectiveAt, Scope readScope) {
    public record Scope(ProjectViewReadScopeApi.ReadScope projectScope) { }
}
