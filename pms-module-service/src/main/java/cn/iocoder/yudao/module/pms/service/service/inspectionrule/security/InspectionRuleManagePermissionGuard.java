package cn.iocoder.yudao.module.pms.service.service.inspectionrule.security;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_MANAGE_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class InspectionRuleManagePermissionGuard {

    public static final String MANAGE_PERMISSION = "pms:inspection-rule:manage";

    private final PermissionApi permissionApi;

    public void check() {
        TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0
                || !permissionApi.hasAnyPermissions(actorId, MANAGE_PERMISSION)) {
            throw exception(INSPECTION_RULE_MANAGE_FORBIDDEN);
        }
    }
}
