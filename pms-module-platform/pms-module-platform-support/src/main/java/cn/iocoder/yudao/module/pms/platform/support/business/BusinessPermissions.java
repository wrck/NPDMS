package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import java.util.Objects;

/** Domain API permissions are inherited once, independent of the template/model workbench permission. */
public final class BusinessPermissions implements BusinessAccessGuard {
    private final PermissionCommonApi permissions;
    private final BusinessCallerContext callers;
    public BusinessPermissions(PermissionCommonApi permissions,BusinessCallerContext callers) { this.permissions=permissions;this.callers=callers; }
    @Override public void requireReadable(BusinessModelDescriptor model,EntityActor actor,String scene) {
        require(actor,model.authorizationPolicyRef());
    }
    @Override public void requireWritable(BusinessModelDescriptor model,EntityActor actor,String scene) {
        if (scene==null || !scene.startsWith("operation:")) BusinessAccessGuard.deny("Business operation permission is required");
        var operation=model.operations().stream().filter(value->value.code().equals(scene.substring("operation:".length()))).findFirst().orElse(null);
        require(actor,operation==null?null:operation.authorizationPolicyRef());
    }
    private void require(EntityActor actor,String permission) {
        var caller=callers.require();
        if (actor==null || actor.isSystemObserver() || !Objects.equals(caller.tenantId(),actor.tenantId())
                || !Objects.equals(caller.userId(),actor.userId()) || permission==null || permission.isBlank()
                || !permissions.hasAnyPermissions(caller.userId(),permission)) BusinessAccessGuard.deny("Business permission denied");
    }
}
