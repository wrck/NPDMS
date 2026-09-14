package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectBusinessExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class SiteSurveyWriteAccess {
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;
    private final ProjectBusinessExecutionApi executions;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long projectId, String permission, ProjectBusinessExecutionSelection selection) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (projectId == null || projectId <= 0 || actorId == null || !permissions.hasAnyPermissions(actorId, permission))
            throw exception(FORBIDDEN);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenantId, actorId, projectId, ProjectScopeApi.ACTION_MANAGE));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) throw exception(FORBIDDEN);
        executions.lockForWrite(new ProjectBusinessExecutionApi.WriteRequest(projectId, "SOL", "SITE_SURVEY", selection));
    }
}
