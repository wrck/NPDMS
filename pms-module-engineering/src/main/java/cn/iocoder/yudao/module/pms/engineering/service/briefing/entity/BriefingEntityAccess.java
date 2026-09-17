package cn.iocoder.yudao.module.pms.engineering.service.briefing.entity;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** 独立交底入口的功能权限和项目范围校验；不要求存在阶段、任务或 WorkBinding。 */
@Service
@RequiredArgsConstructor
public class BriefingEntityAccess {
    public static final String QUERY = "pms:eng-briefing:query";
    public static final String CREATE = "pms:eng-briefing:create";
    public static final String UPDATE = "pms:eng-briefing:update";
    public static final String DELETE = "pms:eng-briefing:delete";
    public static final String GENERATE = "pms:eng-briefing:generate";
    public static final String AUDIT = "pms:eng-briefing:audit";
    public static final String PUBLISH = "pms:eng-briefing:publish";
    private final PermissionApi permissions;
    private final ProjectScopeApi scopes;

    public Long actorId() {
        Long id = SecurityFrameworkUtils.getLoginUserId();
        if (id == null || id <= 0) throw exception(FORBIDDEN);
        return id;
    }

    public void requirePermission(String permission) {
        if (TenantContextHolder.getRequiredTenantId() < 0
                || !permissions.hasAnyPermissions(actorId(), permission)) throw exception(FORBIDDEN);
    }

    public Set<Long> visibleProjects() {
        requirePermission(QUERY);
        Set<Long> result = scopes.resolveAllCurrent(new ProjectAllScopeQuery(
                TenantContextHolder.getRequiredTenantId(), actorId(), ProjectScopeApi.ACTION_VIEW));
        if (result == null || result.stream().anyMatch(id -> id == null || id <= 0))
            throw exception(FORBIDDEN);
        return Set.copyOf(result);
    }

    public void requireReadable(Long projectId) {
        requirePermission(QUERY);
        requireProject(projectId);
        requireFull(scopes.resolveCurrent(new ProjectCurrentScopeQuery(
                TenantContextHolder.getRequiredTenantId(), actorId(), projectId, ProjectScopeApi.ACTION_VIEW)), projectId);
    }

    /** 调用方先锁范围，再锁交底行，禁止采用相反锁顺序。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockWrite(Long projectId, String permission) {
        requirePermission(permission);
        requireProject(projectId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long actorId = actorId();
        var before = scopes.resolveCurrent(new ProjectCurrentScopeQuery(
                tenantId, actorId, projectId, ProjectScopeApi.ACTION_EDIT));
        requireFull(before, projectId);
        var locked = scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(
                tenantId, actorId, projectId, ProjectScopeApi.ACTION_EDIT, before.treeVersion()));
        requireFull(locked, projectId);
        if (!Objects.equals(before.rootProjectId(), locked.rootProjectId())
                || !Objects.equals(before.treeVersion(), locked.treeVersion())) throw exception(FORBIDDEN);
        requirePermission(permission);
    }

    private static void requireProject(Long projectId) {
        if (projectId == null || projectId <= 0) throw exception(FORBIDDEN);
    }

    private static void requireFull(ProjectScopeResult scope, Long projectId) {
        if (scope == null || scope.rootProjectId() == null || scope.treeVersion() == null
                || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId))
            throw exception(FORBIDDEN);
    }
}
