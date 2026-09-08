package cn.iocoder.yudao.module.pms.platform.service.businessview;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewComponentProvider.Context;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
/** PM-03: all callers, including Java consumers, use authenticated context, never request actor. */
@Component
@RequiredArgsConstructor
public class BusinessViewAccess {
    private final PermissionApi permissionApi;
    private final Environment environment;
    public <T> T trusted(Supplier<T> work) {
        if (SecurityFrameworkUtils.getLoginUserId() == null) throw exception(FORBIDDEN);
        if (TenantContextHolder.getTenantId() != null) return work.get();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) throw exception(FORBIDDEN);
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(0L, () -> result.set(work.get()));
        return result.get();
    }
    public Context context() {
        Long tenant = TenantContextHolder.getTenantId();
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (tenant == null || tenant < 0 || actor == null || actor <= 0) throw exception(FORBIDDEN);
        return new Context(tenant, actor);
    }
    public boolean has(Context context, String action) {
        if (!context().equals(context)) throw exception(FORBIDDEN);
        return permissionApi.hasAnyPermissions(context.actorId(), "pms:business-view:" + action);
    }
    public void require(Context context, String action) {
        if (!has(context, action)) throw exception(FORBIDDEN);
    }
}
