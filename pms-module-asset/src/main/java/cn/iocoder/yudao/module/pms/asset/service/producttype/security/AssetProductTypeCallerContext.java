package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_INVALID_REQUEST;

final class AssetProductTypeCallerContext {

    static final String INSPECTION = "INSPECTION";

    private static final ThreadLocal<Deque<AssetProductTypeCaller>> CALLERS = new ThreadLocal<>();

    static <T> T callAsInspection(Supplier<T> action) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || action == null) {
            throw exception(AST_PRODUCT_TYPE_INVALID_REQUEST);
        }
        Deque<AssetProductTypeCaller> callers = CALLERS.get();
        if (callers == null) {
            callers = new ArrayDeque<>();
            CALLERS.set(callers);
        }
        callers.addLast(new AssetProductTypeCaller(INSPECTION, tenantId));
        try {
            return action.get();
        } finally {
            callers.removeLast();
            if (callers.isEmpty()) {
                CALLERS.remove();
            }
        }
    }

    static AssetProductTypeCaller get() {
        Deque<AssetProductTypeCaller> callers = CALLERS.get();
        return callers == null ? null : callers.peekLast();
    }

    static void clear() {
        CALLERS.remove();
    }

    private AssetProductTypeCallerContext() {
    }
}
