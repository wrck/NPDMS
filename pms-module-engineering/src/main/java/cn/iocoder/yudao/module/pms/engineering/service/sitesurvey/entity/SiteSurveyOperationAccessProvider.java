package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationAccessProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Owner permissions and object state only; stage/task eligibility belongs to the caller. */
@Component
@RequiredArgsConstructor
public class SiteSurveyOperationAccessProvider implements ProjectBusinessOperationAccessProvider {
    private final SiteSurveyEntityService surveys;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    @Override public String ownerContext() { return "SOL"; }
    @Override public String objectType() { return "SITE_SURVEY"; }

    @Override public Access inspect(Context context) {
        if (context == null || !Objects.equals(context.tenantId(), TenantContextHolder.getRequiredTenantId())
                || context.actorId() == null || !Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || !permissions.hasAnyPermissions(context.actorId(), "pms:sol-site-survey:query")
                || !scope(context, ProjectScopeApi.ACTION_VIEW)) throw exception(FORBIDDEN);
        boolean manage = scope(context, ProjectScopeApi.ACTION_MANAGE);
        Set<String> actions = new LinkedHashSet<>();
        if (manage && permissions.hasAnyPermissions(context.actorId(), "pms:sol-site-survey:create")) actions.add("SOL.SITE_SURVEY.CREATE");
        if (context.objectId() == null) return new Access(actions, null);
        Long id;
        try { id = Long.valueOf(context.objectId()); } catch (NumberFormatException invalid) { throw exception(FORBIDDEN); }
        var row = surveys.getSiteSurveyEntity(id);
        if (row == null || !Objects.equals(row.getTenantId(), context.tenantId())
                || !Objects.equals(row.getProjectId(), context.projectId()) || Boolean.TRUE.equals(row.getDeleted()))
            throw exception(FORBIDDEN);
        if (row.getStatus() == null || row.getStatus() < 0 || row.getStatus() > 3
                || row.getVersion() == null || row.getVersion() < 0) throw new IllegalStateException("OWNER_STATE_UNAVAILABLE");
        if (manage && permissions.hasAnyPermissions(context.actorId(), "pms:sol-site-survey:update")) {
            if (Integer.valueOf(0).equals(row.getStatus())) actions.addAll(Set.of(
                    "SOL.SITE_SURVEY.UPDATE", "SOL.SITE_SURVEY.CONFIRM", "SOL.SITE_SURVEY.REJECT"));
            if (Integer.valueOf(1).equals(row.getStatus())) actions.add("SOL.SITE_SURVEY.ARCHIVE");
        }
        if (manage && Integer.valueOf(0).equals(row.getStatus()) && row.getOutsourceRequestId() == null
                && permissions.hasAnyPermissions(context.actorId(), "pms:sol-site-survey:delete"))
            actions.add("SOL.SITE_SURVEY.DELETE");
        return new Access(actions, "SOL:SITE_SURVEY:" + row.getId() + ":" + row.getVersion() + ":" + row.getStatus());
    }
    private boolean scope(Context context, String action) {
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(context.tenantId(), context.actorId(), context.projectId(), action));
        return scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(context.projectId());
    }
}
