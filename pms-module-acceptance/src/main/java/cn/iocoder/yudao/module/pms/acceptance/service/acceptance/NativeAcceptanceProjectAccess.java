package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/** Project scope for the native acceptance CRUD entries; command permissions stay at their existing entry points. */
@Component
@RequiredArgsConstructor
public class NativeAcceptanceProjectAccess {
    private final ProjectScopeApi scopes;

    public Set<Long> readableProjects() {
        var result = scopes.resolveAllCurrent(new ProjectAllScopeQuery(
                TenantContextHolder.getRequiredTenantId(), actor(), ProjectScopeApi.ACTION_VIEW));
        return result == null ? Set.of() : result;
    }

    public void requireRead(Long projectId) {
        require(new AccessRequest(projectId, false));
    }

    /** Must be called inside the business write transaction. */
    public void requireWrite(Long projectId) {
        require(new AccessRequest(projectId, true));
    }

    private void require(AccessRequest request) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        Long actor = actor();
        Long projectId = request.projectId();
        if (projectId == null) throw denied();
        String action = request.write() ? ProjectScopeApi.ACTION_MANAGE : ProjectScopeApi.ACTION_VIEW;
        var scope = scopes.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, projectId, action));
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(projectId)) throw denied();
        if (request.write()) {
            var checked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                    tenant, actor, projectId, action, scope.treeVersion()));
            if (checked == null || !Objects.equals(scope.treeVersion(), checked.treeVersion())
                    || checked.fullProjectIds() == null || !checked.fullProjectIds().contains(projectId)) throw denied();
        }
    }

    private static Long actor() {
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || actor <= 0) throw denied();
        return actor;
    }

    private static ServiceException denied() {
        return new ServiceException(FORBIDDEN.getCode(), "当前用户无此项目的验收数据访问权限");
    }

    private record AccessRequest(Long projectId, boolean write) {}
}
