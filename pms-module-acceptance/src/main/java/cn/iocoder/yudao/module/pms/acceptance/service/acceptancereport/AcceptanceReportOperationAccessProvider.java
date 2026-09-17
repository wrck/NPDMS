package cn.iocoder.yudao.module.pms.acceptance.service.acceptancereport;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.ProjectBusinessOperationAccessProvider;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Report-write eligibility does not assert report validity or acceptance completion. */
@Component
@RequiredArgsConstructor
public class AcceptanceReportOperationAccessProvider implements ProjectBusinessOperationAccessProvider {
    private final AcceptanceReportQueryService queries;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    @Override public String ownerContext() { return "ACC"; }
    @Override public String objectType() { return "ACCEPTANCE"; }

    @Override public Access inspect(Context context) {
        if (context == null || !Objects.equals(context.tenantId(), TenantContextHolder.getRequiredTenantId())
                || context.actorId() == null || !Objects.equals(context.actorId(), SecurityFrameworkUtils.getLoginUserId())
                || !permissions.hasAnyPermissions(context.actorId(), "pms:acceptance:report:query")) throw exception(FORBIDDEN);
        var view = scopes.resolveCurrent(new ProjectCurrentScopeQuery(context.tenantId(), context.actorId(), context.projectId(), ProjectScopeApi.ACTION_VIEW));
        if (view == null || view.fullProjectIds() == null || !view.fullProjectIds().contains(context.projectId())) throw exception(FORBIDDEN);
        if (context.objectId() == null) return new Access(Set.of(), null);
        Long id;
        try { id = Long.valueOf(context.objectId()); } catch (NumberFormatException invalid) { throw exception(FORBIDDEN); }
        var activity = queries.get(id, new AcceptanceReportQueryService.Actor(context.tenantId(), context.actorId()));
        if (activity == null || activity.version() == null || activity.version() < 0
                || !Objects.equals(activity.projectId(), context.projectId())) throw exception(FORBIDDEN);
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(context.tenantId(), context.actorId(), context.projectId(), ProjectScopeApi.ACTION_EDIT));
        boolean writable = permissions.hasAnyPermissions(context.actorId(), "pms:acceptance:report:write")
                && scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(context.projectId());
        // The selected report's version/content is still validated by each typed Owner command.
        return new Access(writable ? Set.of("ACC.ACCEPTANCE_REPORT.CREATE_DRAFT", "ACC.ACCEPTANCE_REPORT.UPDATE_DRAFT",
                "ACC.ACCEPTANCE_REPORT.PUBLISH", "ACC.ACCEPTANCE_REPORT.REVOKE") : Set.of(),
                "ACC:ACCEPTANCE:" + activity.id() + ":" + activity.version());
    }
}
