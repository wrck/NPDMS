package cn.iocoder.yudao.module.pms.platform.support.business;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionBatchQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessCallerContext;
import java.util.HashMap;
import java.util.HashSet;
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
        require(actor,operationPolicy(model,scene));
    }
    private String operationPolicy(BusinessModelDescriptor model,String scene) {
        if (scene==null || !scene.startsWith("operation:")) BusinessAccessGuard.deny("Business operation permission is required");
        var operation=model.operations().stream().filter(value->value.code().equals(scene.substring("operation:".length()))).findFirst().orElse(null);
        return operation==null?null:operation.authorizationPolicyRef();
    }
    private record DisplayPermission(Long tenantId,Long userId,String policy) {}
    /** One model projection may have several buttons governed by the same policy. Never used for commands. */
    BusinessAccessGuard forOperationDisplay() {
        return forReadProjection(false);
    }
    /** Query and button decisions share one observation during a single page initialization. */
    BusinessAccessGuard forPageRead() {
        return forReadProjection(true);
    }
    private BusinessAccessGuard forReadProjection(boolean includeQuery) {
        var decisions=new HashMap<DisplayPermission,Boolean>();
        return new BusinessAccessGuard() {
            @Override public void requireReadable(BusinessModelDescriptor model,EntityActor actor,String scene) {
                if(includeQuery)requireObserved(model,actor,model.authorizationPolicyRef());
                else BusinessPermissions.this.requireReadable(model,actor,scene);
            }
            @Override public void requireWritable(BusinessModelDescriptor model,EntityActor actor,String scene) {
                requireObserved(model,actor,operationPolicy(model,scene));
            }
            private void requireObserved(BusinessModelDescriptor model,EntityActor actor,String policy) {
                requireCaller(actor,policy);
                var key=new DisplayPermission(actor.tenantId(),actor.userId(),policy);
                if(!decisions.containsKey(key) && permissions instanceof PermissionBatchQueryApi batches) {
                    var policies=new HashSet<String>();
                    if(includeQuery && model.authorizationPolicyRef()!=null)policies.add(model.authorizationPolicyRef());
                    for(var operation:model.operations()) {
                        var code=operation.authorizationPolicyRef();
                        if(code!=null&&!code.isBlank())policies.add(code);
                    }
                    var observed=batches.queryPermissions(actor.userId(),policies);
                    policies.forEach(code->decisions.put(new DisplayPermission(actor.tenantId(),actor.userId(),code),Boolean.TRUE.equals(observed.get(code))));
                }
                if(!decisions.computeIfAbsent(key,ignored->permissions.hasAnyPermissions(actor.userId(),policy)))
                    BusinessAccessGuard.deny("Business permission denied");
            }
        };
    }
    private void require(EntityActor actor,String permission) {
        requireCaller(actor,permission);
        if(!permissions.hasAnyPermissions(actor.userId(),permission))BusinessAccessGuard.deny("Business permission denied");
    }
    private void requireCaller(EntityActor actor,String permission) {
        var caller=callers.require();
        if (actor==null || actor.isSystemObserver() || !Objects.equals(caller.tenantId(),actor.tenantId())
                || !Objects.equals(caller.userId(),actor.userId()) || permission==null || permission.isBlank())
            BusinessAccessGuard.deny("Business permission denied");
    }
}
