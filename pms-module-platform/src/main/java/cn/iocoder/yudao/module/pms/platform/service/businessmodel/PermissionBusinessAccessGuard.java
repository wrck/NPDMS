package cn.iocoder.yudao.module.pms.platform.service.businessmodel;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelDescriptor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessOwnerPermissionPolicy;

/**
 * 默认访问守卫：authorizationPolicyRef 非空时映射为权限编码校验；
 * 空权限集合必须拒绝。系统观察者仅限平台内部事实观察（构造点在服务端，
 * HTTP 请求无法注入），不代替任何真实用户的授权。
 */
@Component
public class PermissionBusinessAccessGuard implements BusinessAccessGuard {

    private final PermissionCommonApi permissionApi;
    private final ObjectProvider<BusinessOwnerPermissionPolicy> ownerPolicies;

    public PermissionBusinessAccessGuard(PermissionCommonApi permissionApi) {
        this(permissionApi, null);
    }

    @Autowired
    public PermissionBusinessAccessGuard(PermissionCommonApi permissionApi,
            ObjectProvider<BusinessOwnerPermissionPolicy> ownerPolicies) {
        this.permissionApi = permissionApi;
        this.ownerPolicies = ownerPolicies;
    }

    @Override
    public void requireReadable(BusinessModelDescriptor descriptor, EntityActor actor, String sceneCode) {
        requireTrustedActor(actor);
        var owner = ownerPolicy(descriptor);
        if (owner != null) {
            if (actor == null || !owner.readable(actor)) BusinessAccessGuard.deny("业务读取权限不足: " + descriptor.stableCode());
            return;
        }
        require(descriptor, actor, "read");
    }

    @Override
    public void requireWritable(BusinessModelDescriptor descriptor, EntityActor actor, String sceneCode) {
        requireTrustedActor(actor);
        var owner = ownerPolicy(descriptor);
        if (owner != null && sceneCode != null && sceneCode.startsWith("operation:")) {
            String code = sceneCode.substring("operation:".length());
            var operation = descriptor.operations().stream().filter(item -> item.code().equals(code)).findFirst().orElse(null);
            if (actor == null || actor.isSystemObserver() || operation == null
                    || !permissionApi.hasAnyPermissions(actor.userId(), "pms:business-model:operate")
                    || !owner.executable(actor, code, operation.version()))
                BusinessAccessGuard.deny("业务操作权限不足: " + descriptor.stableCode() + " " + code);
            return;
        }
        if (sceneCode == null || !sceneCode.startsWith("operation:") || actor == null || actor.isSystemObserver())
            BusinessAccessGuard.deny("缺少可信业务操作授权上下文: " + descriptor.stableCode());
        String code = sceneCode.substring("operation:".length());
        var operation = descriptor.operations().stream().filter(item -> item.code().equals(code)).findFirst().orElse(null);
        if (operation == null || operation.authorizationPolicyRef() == null || operation.authorizationPolicyRef().isBlank())
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                    "OPERATION_PERMISSION_NOT_DECLARED", "默认操作缺少独立权限声明: " + descriptor.stableCode() + "/" + code);
        if (!permissionApi.hasAnyPermissions(actor.userId(), "pms:business-model:operate")
                || !permissionApi.hasAnyPermissions(actor.userId(), operation.authorizationPolicyRef()))
            BusinessAccessGuard.deny("业务操作权限不足: " + descriptor.stableCode() + " " + code);
    }

    private void requireTrustedActor(EntityActor actor) {
        if(actor!=null && actor.isSystemObserver()) return;
        var user=cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUser();
        Long tenant=cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getTenantId();
        if(actor==null || user==null || !java.util.Objects.equals(user.getId(),actor.userId())
                || tenant==null || !java.util.Objects.equals(tenant,actor.tenantId()) || !java.util.Objects.equals(user.getTenantId(),tenant))
            BusinessAccessGuard.deny("Business actor and tenant must match the authenticated identity");
    }

    private BusinessOwnerPermissionPolicy ownerPolicy(BusinessModelDescriptor descriptor) {
        if (ownerPolicies == null) return null;
        var matches = ownerPolicies.stream().filter(item -> descriptor.ownerModule().equals(item.ownerModule())
                && descriptor.entityType().equals(item.entityType())).toList();
        if (matches.size() > 1) BusinessAccessGuard.deny("业务权限声明重复: " + descriptor.stableCode());
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private void require(BusinessModelDescriptor descriptor, EntityActor actor, String action) {
        String policyRef = descriptor.authorizationPolicyRef();
        if (policyRef == null || policyRef.isBlank()) {
            if (actor != null && actor.isSystemObserver()) return;
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException(
                    "READ_PERMISSION_NOT_DECLARED", "默认读取缺少权限声明: " + descriptor.stableCode());
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
