package cn.iocoder.yudao.module.pms.customer.service.security;

import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleApi;
import cn.iocoder.yudao.module.system.api.permission.dto.RoleRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomerScopeContextService {

    @Resource
    private PermissionApi permissionApi;
    @Resource
    private RoleApi roleApi;
    @Resource
    private CustomerScopeResolver scopeResolver;

    public CustomerVisibleScope resolve(Long tenantId, Long userId) {
        Set<Long> roleIds = permissionApi.getRoleIdListByUserId(userId);
        if (roleIds == null) {
            throw new IllegalStateException("客户权限角色范围不可用");
        }
        List<RoleRespDTO> roles = roleApi.getRoleList(roleIds);
        if (roles == null) {
            throw new IllegalStateException("客户权限角色详情不可用");
        }
        Set<String> roleCodes = roles.stream()
                .map(role -> role.getCode())
                .collect(Collectors.toUnmodifiableSet());
        return scopeResolver.resolve(new CustomerScopeRequest(tenantId, userId, roleIds, roleCodes, false));
    }
}
