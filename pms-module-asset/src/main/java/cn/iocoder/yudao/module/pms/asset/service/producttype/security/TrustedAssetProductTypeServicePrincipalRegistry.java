package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED;

@Component
final class TrustedAssetProductTypeServicePrincipalRegistry {

    private final Map<String, TrustedPrincipal> trustedServicePrincipals;

    TrustedAssetProductTypeServicePrincipalRegistry(
            AssetProductTypeServicePrincipalProperties properties) {
        Map<String, TrustedPrincipal> configuredPrincipals = properties.getTrustedServicePrincipals();
        trustedServicePrincipals = configuredPrincipals == null ? Map.of() : Map.copyOf(configuredPrincipals);
    }

    Long resolve(String consumerCode, String actionCode) {
        if (consumerCode == null || consumerCode.isBlank()
                || actionCode == null || !AssetProductTypeActionCodes.ALL.contains(actionCode)) {
            throw exception(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED);
        }
        TrustedPrincipal principal = trustedServicePrincipals.get(consumerCode.trim());
        if (principal == null || principal.principalId() == null || principal.principalId() <= 0
                || !principal.allowedActions().contains(actionCode)
                || isInspectionWriteAction(consumerCode, principal.allowedActions())) {
            throw exception(AST_PRODUCT_TYPE_SERVICE_IDENTITY_REJECTED);
        }
        return principal.principalId();
    }

    private boolean isInspectionWriteAction(String consumerCode, Set<String> allowedActions) {
        return AssetProductTypeCallerContext.INSPECTION.equals(consumerCode.trim())
                && allowedActions.contains(AssetProductTypeActionCodes.PRODUCT_TYPE_CONTROLLED_IMPORT);
    }

    record TrustedPrincipal(Long principalId, Set<String> allowedActions) {

        TrustedPrincipal {
            allowedActions = allowedActions == null ? Set.of() : Set.copyOf(allowedActions);
        }
    }
}
