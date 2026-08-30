package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_INVALID_REQUEST;

@Component
@RequiredArgsConstructor
public class AssetProductTypeRequestGuard {

    private final TrustedAssetProductTypeServicePrincipalRegistry principalRegistry;

    public Long requireTrustedPrincipal(String actionCode) {
        Long tenantId = TenantContextHolder.getTenantId();
        AssetProductTypeCaller caller = AssetProductTypeCallerContext.get();
        if (tenantId == null || caller == null || !Objects.equals(tenantId, caller.tenantId())) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        return principalRegistry.resolve(caller.consumerCode(), actionCode);
    }

    public Long requireSubjectUser(Long subjectUserId) {
        if (subjectUserId == null || subjectUserId <= 0) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        return subjectUserId;
    }
}
