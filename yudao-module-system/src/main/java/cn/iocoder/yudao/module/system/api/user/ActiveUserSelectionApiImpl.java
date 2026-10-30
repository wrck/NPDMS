package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.user.AdminUserMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.ActiveUserSelectionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID_ARGUMENT;

@Service
@RequiredArgsConstructor
public class ActiveUserSelectionApiImpl implements ActiveUserSelectionApi {
    private final AdminUserMapper userMapper;
    private final org.springframework.beans.factory.ObjectProvider<ProjectMemberIdentityScopeProvider> projectMemberScopes;
    private final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;

    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper memberDepartments;

    @Override
    @DataPermission(enable = true) // Department metadata and the scope owner must never inherit an outer directory exemption.
    public PageResult<MemberOption> pageProjectMembers(MemberFilterQuery query) {
        if (query == null || query.field() == null || query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100
                || query.keyword() != null && query.keyword().length() > 64) throw exception(ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        var actor = cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser();
        var tenant = TenantContextHolder.getTenantId();
        if (actor == null || actor.getId() == null || actor.getId() <= 0 || tenant == null
                || !tenant.equals(actor.getTenantId()) || !permissions.hasAnyPermissions(actor.getId(), "pms:project:query"))
            throw exception(cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
        var owner = projectMemberScopes.getIfAvailable();
        if (owner == null) throw new IllegalStateException("Project member scope provider is unavailable");
        // The owner executes with ordinary data permissions; only the subsequent scoped identity lookup is exempt.
        var ids = owner.resolveCurrent(new ProjectMemberIdentityScopeProvider.Query(tenant, actor.getId(), query.field()));
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalStateException("Project member scope is invalid");
        if (ids.isEmpty()) return PageResult.empty();
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        Set<Long> matchedDepartments = Set.of();
        var departmentNames = new java.util.HashMap<Long, String>();
        if (keyword != null) {
            var departmentIds = cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils.executeIgnore(() ->
                    userMapper.selectScopedActiveUserDepartmentIds(new cn.iocoder.yudao.module.system.dal.mysql.user.query.ScopedActiveUserDepartmentsQuery(tenant, Set.copyOf(ids))));
            if (!departmentIds.isEmpty()) {
                for (var department : memberDepartments.selectProjectMemberDepartments(
                        new cn.iocoder.yudao.module.system.dal.mysql.dept.query.ProjectMemberDepartmentsQuery(tenant, departmentIds, keyword)))
                    departmentNames.put(department.getId(), department.getName());
                matchedDepartments = Set.copyOf(departmentNames.keySet());
            }
        }
        var selection = new cn.iocoder.yudao.module.system.dal.mysql.user.query.ScopedActiveUserPageQuery(
                tenant, Set.copyOf(ids), keyword, query.pageNo(), query.pageSize(), matchedDepartments);
        PageResult<cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO> page = cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils.executeIgnore(() -> {
            long total = userMapper.selectScopedActiveUserCount(selection);
            if (total == 0) return PageResult.<cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO>empty();
            return new PageResult<>(userMapper.selectScopedActiveUserPage(selection), total);
        });
        var pageDepartmentIds = page.getList().stream().map(cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO::getDeptId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        // This request already obtained matching names with ordinary department permission.
        var missingDepartmentIds = new java.util.HashSet<>(pageDepartmentIds);
        missingDepartmentIds.removeAll(departmentNames.keySet());
        if (!missingDepartmentIds.isEmpty()) {
            for (var department : memberDepartments.selectProjectMemberDepartments(
                    new cn.iocoder.yudao.module.system.dal.mysql.dept.query.ProjectMemberDepartmentsQuery(tenant, missingDepartmentIds, null)))
                departmentNames.put(department.getId(), department.getName());
        }
        return new PageResult<>(page.getList().stream().map(user -> new MemberOption(user.getId(), user.getUsername(),
                user.getNickname(), user.getDeptId(), user.getDeptId() == null ? null : departmentNames.get(user.getDeptId()))).toList(), page.getTotal());
    }


    @Override
    @DataPermission(enable = false) // 专项裁决：项目调用方已授权；保留租户、系统角色和适用组织资格。
    public PageResult<User> page(Query query) {
        if (query == null || query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100
                || query.roleCode() == null || !Set.of("SERVICE_MANAGER", "PROJECT_MANAGER", "SALES_REPRESENTATIVE").contains(query.roleCode())
                || query.keyword() != null && query.keyword().length() > 64
                || query.userIds() != null && query.userIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw exception(ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (query.userIds() != null && query.userIds().isEmpty()) return PageResult.empty();
        Qualification scope = query.qualification();
        if (scope != null && (scope.companyId() == null || scope.companyId() <= 0
                || scope.departmentId() != null && (scope.departmentId() <= 0 || scope.departmentCode() == null || scope.departmentCode().isBlank())
                || scope.scopeRole() != null && !"PROJECT_MANAGER".equals(scope.scopeRole()))) {
            throw exception(ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        }
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        var selection = ActiveUserSelectionQuery.builder().tenantId(tenantId).pageNo(query.pageNo())
                .pageSize(query.pageSize()).keyword(keyword).roleCode(query.roleCode())
                .userIds(query.userIds() == null ? null : Set.copyOf(query.userIds()))
                .companyId(scope == null ? null : scope.companyId()).departmentId(scope == null ? null : scope.departmentId())
                .departmentCode(scope == null ? null : scope.departmentCode()).scopeRole(scope == null ? null : scope.scopeRole())
                .effectiveAt(LocalDateTime.now()).build();
        long total = userMapper.selectActiveSelectionCount(selection);
        if (total == 0) return PageResult.empty();
        return new PageResult<>(userMapper.selectActiveSelectionPage(selection).stream().map(user ->
                new User(user.getId(), user.getUsername(), user.getNickname(), user.getDeptId())).toList(),
                total);
    }
}
