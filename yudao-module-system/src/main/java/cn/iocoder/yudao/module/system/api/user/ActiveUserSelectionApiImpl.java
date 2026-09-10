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
