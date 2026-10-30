package cn.iocoder.yudao.module.system.api.user;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.datapermission.core.annotation.DataPermission;
import cn.iocoder.yudao.framework.datapermission.core.util.DataPermissionUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi.MemberOption;
import cn.iocoder.yudao.module.system.dal.dataobject.user.AdminUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.dept.DeptMapper;
import cn.iocoder.yudao.module.system.dal.mysql.dept.query.ProjectMemberDepartmentsQuery;
import cn.iocoder.yudao.module.system.dal.mysql.user.ProjectMemberRoleSelectionMapper;
import cn.iocoder.yudao.module.system.dal.mysql.user.query.ProjectMemberRoleUserPageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.ORGANIZATION_SCOPE_INVALID_ARGUMENT;

/** 列表角色候选独立于原FULL成员候选，保留原稀疏响应与部门元数据权限。 */
@Service
@RequiredArgsConstructor
public class ProjectMemberRoleSelectionApiImpl implements ProjectMemberRoleSelectionApi {
    private static final Set<String> ROLE_CODES = Set.of("PROJECT_MANAGER", "SERVICE_MANAGER",
            "SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2", "SALES_REPRESENTATIVE");
    private final ProjectMemberRoleSelectionMapper users;
    private final DeptMapper departments;
    private final PermissionApi permissions;

    @Override
    @DataPermission(enable = true)
    public PageResult<MemberOption> page(Query query) {
        if (query == null || query.pageNo() < 1 || query.pageSize() < 1 || query.pageSize() > 100
                || query.keyword() != null && query.keyword().length() > 64 || query.roleCodes() == null
                || query.roleCodes().stream().anyMatch(role -> role == null || !ROLE_CODES.contains(role))) {
            throw exception(ORGANIZATION_SCOPE_INVALID_ARGUMENT);
        }
        var actor = SecurityFrameworkUtils.getLoginUser();
        Long tenant = TenantContextHolder.getTenantId();
        if (actor == null || actor.getId() == null || actor.getId() <= 0 || tenant == null
                || !tenant.equals(actor.getTenantId())
                || !permissions.hasAnyPermissions(actor.getId(), "pms:project:query")) {
            throw exception(FORBIDDEN);
        }
        if (query.roleCodes().isEmpty()) return PageResult.empty();
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        var roleCodes = Set.copyOf(query.roleCodes());
        var identity = new ProjectMemberRoleUserPageQuery(tenant, roleCodes, keyword,
                query.pageNo(), query.pageSize(), Set.of());
        var names = new HashMap<Long, String>();
        if (keyword != null) {
            var departmentIds = DataPermissionUtils.executeIgnore(() -> users.selectDepartmentIds(identity));
            if (!departmentIds.isEmpty()) {
                for (var department : departments.selectProjectMemberDepartments(
                        new ProjectMemberDepartmentsQuery(tenant, departmentIds, keyword))) {
                    names.put(department.getId(), department.getName());
                }
            }
        }
        var selection = new ProjectMemberRoleUserPageQuery(tenant, roleCodes, keyword,
                query.pageNo(), query.pageSize(), Set.copyOf(names.keySet()));
        PageResult<AdminUserDO> page = DataPermissionUtils.executeIgnore(() -> {
            long total = users.selectCount(selection);
            return total == 0 ? PageResult.empty() : new PageResult<>(users.selectPage(selection), total);
        });
        var missingDepartments = page.getList().stream().map(AdminUserDO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        missingDepartments.removeAll(names.keySet());
        if (!missingDepartments.isEmpty()) {
            for (var department : departments.selectProjectMemberDepartments(
                    new ProjectMemberDepartmentsQuery(tenant, missingDepartments, null))) {
                names.put(department.getId(), department.getName());
            }
        }
        return new PageResult<>(page.getList().stream().map(user -> new MemberOption(user.getId(), user.getUsername(),
                user.getNickname(), user.getDeptId(), names.get(user.getDeptId()))).toList(), page.getTotal());
    }
}
