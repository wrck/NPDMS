package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;

@Component @RequiredArgsConstructor
public class ProjectDeliverableAccess {
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;

    public boolean writable(ProjectDeliverableRuleApi.Context context) {
        try {
            require(context, true, false);
            return true;
        } catch (ServiceException denied) {
            return false;
        }
    }

    public Long require(ProjectDeliverableRuleApi.Context context, boolean write, boolean lock) {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        check(context, TenantContextHolder.getRequiredTenantId(), actor, write, lock, null);
        return actor;
    }

    public Long check(ProjectDeliverableRuleApi.Context context, Long tenant, Long actor, boolean write, boolean lock, Long expectedScope) {
        return checkProject(context.projectId(),context.lifecycleStatus(),tenant,actor,write,lock,expectedScope);
    }

    public Long checkProject(Long projectId, String lifecycleStatus, Long tenant, Long actor,
                             boolean write, boolean lock, Long expectedScope) {
        if (!Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || actor == null || actor <= 0
                || !permissions.hasAnyPermissions(actor, write ? "pms:project:update" : "pms:project:query"))
            throw failure("无项目交付件操作权限");
        String action = write ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW;
        var observed = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, projectId, action));
        if (observed == null || observed.treeVersion() == null || observed.fullProjectIds() == null
                || !observed.fullProjectIds().contains(projectId)) throw failure("项目不在授权范围内");
        if (expectedScope != null && !Objects.equals(expectedScope, observed.treeVersion())) throw failure("项目权限范围已变化，请刷新");
        var scope = lock ? scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, actor, projectId,
                action, observed.treeVersion())) : observed;
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)
                || !Objects.equals(observed.treeVersion(), scope.treeVersion())) throw failure("项目权限范围已变化，请刷新");
        if (write && !"ACTIVE".equals(lifecycleStatus))
            throw failure("仅进行中的项目可提交交付件");
        return scope.treeVersion();
    }

    public static ServiceException failure(String reason) { return new ServiceException(1_014_013_002, reason); }
}
