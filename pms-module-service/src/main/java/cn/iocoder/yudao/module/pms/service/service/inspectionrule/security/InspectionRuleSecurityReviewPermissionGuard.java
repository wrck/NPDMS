package cn.iocoder.yudao.module.pms.service.service.inspectionrule.security;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

@RequiredArgsConstructor
public class InspectionRuleSecurityReviewPermissionGuard {

    public static final String REVIEW_PERMISSION = "pms:inspection-rule:security-review";
    public static final String AUTHORIZATION_TYPE = "RBAC_PERMISSION";

    private final InspectionRuleExplicitAuthorizationApi authorizationApi;

    public ReviewAuthorization check() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null) {
            throw new SecurityException("inspection rule security review permission required");
        }
        InspectionRuleExplicitAuthorizationApi.ExplicitAuthorization authorization =
                authorizationApi.findExplicitAuthorization(tenantId, actorId, REVIEW_PERMISSION);
        if (authorization == null
                || !Objects.equals(tenantId, authorization.tenantId())
                || !Objects.equals(actorId, authorization.actorId())
                || !Objects.equals(REVIEW_PERMISSION, authorization.permissionCode())
                || !Objects.equals(AUTHORIZATION_TYPE, authorization.authorizationType())) {
            throw new SecurityException("inspection rule security review permission required");
        }
        return new ReviewAuthorization(
                actorId,
                authorization.permissionCode(),
                authorization.authorizationType(),
                authorization.authorizationSourceId());
    }

    public record ReviewAuthorization(
            Long actorId,
            String permissionCode,
            String authorizationType,
            String authorizationSourceId) {
    }
}
