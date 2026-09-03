package cn.iocoder.yudao.module.pms.service.service.inspectionrule.security;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_SECURITY_REVIEW_FORBIDDEN;

@Service
@RequiredArgsConstructor
public class InspectionRuleSecurityReviewPermissionGuard {

    public static final String REVIEW_PERMISSION = "pms:inspection-rule:security-review";
    public static final String AUTHORIZATION_TYPE = "RBAC_PERMISSION";

    private final PermissionApi permissionApi;

    public ReviewAuthorization check() {
        TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId <= 0) {
            throw exception(INSPECTION_RULE_SECURITY_REVIEW_FORBIDDEN);
        }
        boolean authorized;
        try {
            authorized = permissionApi.hasAnyPermissions(actorId, REVIEW_PERMISSION);
        } catch (RuntimeException ex) {
            throw exception(INSPECTION_RULE_SECURITY_REVIEW_FORBIDDEN);
        }
        if (!authorized) {
            throw exception(INSPECTION_RULE_SECURITY_REVIEW_FORBIDDEN);
        }
        return new ReviewAuthorization(
                actorId,
                REVIEW_PERMISSION,
                AUTHORIZATION_TYPE,
                null);
    }

    public record ReviewAuthorization(
            Long actorId,
            String permissionCode,
            String authorizationType,
            String authorizationSourceId) {
    }
}
