package cn.iocoder.yudao.module.pms.asset.service.producttype.security;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.AST_PRODUCT_TYPE_INVALID_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetProductTypeCallerContextTest {

    @AfterEach
    void clearContext() {
        AssetProductTypeCallerContext.clear();
        TenantContextHolder.clear();
    }

    @Test
    void shouldRestoreNestedContextAndClearAfterCall() {
        TenantContextHolder.setTenantId(1L);

        String result = AssetProductTypeCallerContext.callAsInspection(() -> {
            AssetProductTypeCaller outer = AssetProductTypeCallerContext.get();
            assertEquals(AssetProductTypeCallerContext.INSPECTION, outer.consumerCode());
            assertEquals(1L, outer.tenantId());
            return AssetProductTypeCallerContext.callAsInspection(() -> {
                assertEquals(outer, AssetProductTypeCallerContext.get());
                return "ok";
            });
        });

        assertEquals("ok", result);
        assertNull(AssetProductTypeCallerContext.get());
    }

    @Test
    void shouldClearContextAfterFailure() {
        TenantContextHolder.setTenantId(1L);

        assertThrows(IllegalStateException.class, () -> AssetProductTypeCallerContext.callAsInspection(() -> {
            throw new IllegalStateException("failed");
        }));

        assertNull(AssetProductTypeCallerContext.get());
    }

    @Test
    void shouldNotPropagateCallerToAnotherThread() throws InterruptedException {
        TenantContextHolder.setTenantId(1L);
        AtomicReference<AssetProductTypeCaller> caller = new AtomicReference<>();

        AssetProductTypeCallerContext.callAsInspection(() -> {
            Thread thread = new Thread(() -> caller.set(AssetProductTypeCallerContext.get()));
            thread.start();
            try {
                thread.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return null;
        });

        assertNull(caller.get());
    }

    @Test
    void shouldRejectMissingTenantAndKeepContextTypesNonPublic() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> AssetProductTypeCallerContext.callAsInspection(() -> null));

        assertEquals(AST_PRODUCT_TYPE_INVALID_REQUEST.getCode(), error.getCode());
        assertFalse(Modifier.isPublic(AssetProductTypeCaller.class.getModifiers()));
        assertFalse(Modifier.isPublic(AssetProductTypeCallerContext.class.getModifiers()));
    }
}
