package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import org.springframework.stereotype.Component;

/**
 * 默认访问守卫：authorizationPolicyRef 非空时映射为权限编码校验；
 * 空权限集合必须拒绝。系统观察者仅限平台内部事实观察（构造点在服务端，
 * HTTP 请求无法注入），不代替任何真实用户的授权。
 */
@Component
public class PermissionBusinessAccessGuard implements BusinessAccessGuard {

    private final PermissionCommonApi permissionApi;

    public PermissionBusinessAccessGuard(PermissionCommonApi permissionApi) {
        this.permissionApi = permissionApi;
    }

    @Override
    public void requireReadable(BusinessModelDescriptor descriptor, EntityActor actor, String sceneCode) {
        require(descriptor, actor, "read");
    }

    @Override
    public void requireWritable(BusinessModelDescriptor descriptor, EntityActor actor, String sceneCode) {
        require(descriptor, actor, "write");
    }

    private void require(BusinessModelDescriptor descriptor, EntityActor actor, String action) {
        String policyRef = descriptor.authorizationPolicyRef();
        if (policyRef == null || policyRef.isBlank()) {
            return;
        }
        if (actor != null && actor.isSystemObserver()) {
            return;
        }
        if (actor == null || actor.userId() == null
                || !permissionApi.hasAnyPermissions(actor.userId(), policyRef)) {
            BusinessAccessGuard.deny("权限不足: " + descriptor.stableCode() + " " + action);
        }
    }
}
