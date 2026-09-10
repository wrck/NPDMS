package cn.iocoder.yudao.module.system.api.permission;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.dal.mysql.permission.ExplicitPermissionMapper;
import cn.iocoder.yudao.module.system.dal.mysql.permission.query.ExplicitPermissionQuery;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExplicitPermissionApiImpl implements ExplicitPermissionApi {

    @Resource
    private ExplicitPermissionMapper explicitPermissionMapper;

    @Override
    public boolean hasExplicitPermission(Long tenantId, Long userId, String permission) {
        if (!validContext(tenantId, userId, permission)) {
            return false;
        }
        return explicitPermissionMapper.existsExplicitPermission(query(tenantId, userId, permission));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockAndCheck(Long tenantId, Long userId, String permission) {
        if (!validContext(tenantId, userId, permission)) {
            return false;
        }
        ExplicitPermissionQuery query = query(tenantId, userId, permission);
        if (explicitPermissionMapper.selectActiveUserForUpdate(query) == null) {
            return false;
        }
        List<Long> roles = explicitPermissionMapper.selectRoleIdsForUpdate(query);
        if (roles.isEmpty()) {
            return false;
        }
        query = query.toBuilder().roleIds(roles).build();
        List<Long> grantedRoles = explicitPermissionMapper.selectUserRoleIdsForUpdate(query);
        if (grantedRoles.isEmpty()) {
            return false;
        }
        query = query.toBuilder().roleIds(grantedRoles).build();
        List<Long> menus = explicitPermissionMapper.selectRoleMenuIdsForUpdate(query);
        if (menus.isEmpty()) {
            return false;
        }
        query = query.toBuilder().menuIds(menus).build();
        return !explicitPermissionMapper.selectPermittedMenuIdsForUpdate(query).isEmpty();
    }

    private static boolean validContext(Long tenantId, Long userId, String permission) {
        return tenantId != null && tenantId >= 0 && tenantId.equals(TenantContextHolder.getTenantId())
                && userId != null && userId > 0
                && permission != null && !permission.isBlank() && permission.length() <= 100;
    }

    private static ExplicitPermissionQuery query(Long tenantId, Long userId, String permission) {
        return ExplicitPermissionQuery.builder().tenantId(tenantId).userId(userId)
                .permission(permission).enabledStatus(CommonStatusEnum.ENABLE.getStatus()).build();
    }
}
