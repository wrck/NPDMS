package cn.iocoder.yudao.module.pms.service.service.inspectionrule.security;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_DISABLE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_PUBLISH_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class InspectionRuleActionPermissionGuard {

    public static final String PUBLISH_PERMISSION = "pms:inspection-rule:publish";
    public static final String DISABLE_PERMISSION = "pms:inspection-rule:disable";

    private final PermissionApi permissionApi;

    public void checkPublish() {
        check(PUBLISH_PERMISSION, true);
    }

    public void checkDisable() {
        check(DISABLE_PERMISSION, false);
    }

    private void check(String permission, boolean publish) {
        TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0 || !permissionApi.hasAnyPermissions(actorId, permission)) {
            throw exception(publish ? INSPECTION_RULE_PUBLISH_FORBIDDEN : INSPECTION_RULE_DISABLE_FORBIDDEN);
        }
    }
}
