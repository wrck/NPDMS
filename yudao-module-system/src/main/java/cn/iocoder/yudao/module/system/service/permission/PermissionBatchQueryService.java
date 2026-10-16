package cn.iocoder.yudao.module.system.service.permission;

import cn.iocoder.yudao.module.system.dal.dataobject.permission.RoleDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/** An independent display query; existing single checks continue to authorize commands. */
@Service
@RequiredArgsConstructor
public class PermissionBatchQueryService {
    private final PermissionServiceImpl permissions;
    private final RoleService roles;

    public Map<String, Boolean> queryPermissions(Long userId, Set<String> permissionCodes) {
        if (userId == null || userId <= 0 || permissionCodes == null
                || permissionCodes.stream().anyMatch(code -> code == null || code.isBlank()))
            throw new IllegalArgumentException("PERMISSION_QUERY_INVALID");
        if (permissionCodes.isEmpty()) return Map.of();
        var enabledRoles = permissions.getEnableUserRoleListByUserIdFromCache(userId);
        var result = new LinkedHashMap<String, Boolean>();
        Boolean superAdmin = null;
        for (String code : permissionCodes) {
            boolean allowed = !enabledRoles.isEmpty() && permissions.hasAnyPermission(enabledRoles, code);
            if (!allowed && !enabledRoles.isEmpty()) {
                if (superAdmin == null) superAdmin = roles.hasAnySuperAdmin(convertSet(enabledRoles, RoleDO::getId));
                allowed = superAdmin;
            }
            result.put(code, allowed);
        }
        return Map.copyOf(result);
    }
}
