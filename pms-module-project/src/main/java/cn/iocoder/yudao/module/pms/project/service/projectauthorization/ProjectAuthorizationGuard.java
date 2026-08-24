package cn.iocoder.yudao.module.pms.project.service.projectauthorization;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_MANAGE;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_NOT_FOUND;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class ProjectAuthorizationGuard {

    public static final String SCOPE_CURRENT = "CURRENT_PROJECT";
    public static final String SCOPE_DESCENDANTS = "PROJECT_AND_DESCENDANTS";
    public static final String PERMISSION_QUERY = "pms:project:authorization:query";
    public static final String PERMISSION_MANAGE = "pms:project:authorization:manage";
    public static final String PERMISSION_REVOKE = "pms:project:authorization:revoke";
    private static final Set<String> SERVICE_MANAGER_ROLES =
            Set.of("SERVICE_MANAGER_L1", "SERVICE_MANAGER_L2");

    private final PermissionApi permissionApi;
    private final ProjectMasterMapper projectMapper;
    private final ProjectMemberAssignmentMapper memberMapper;
    private final ProjectTreeVersionMapper versionMapper;
    private final ProjectTreePathMapper pathMapper;
    private final ProjectScopeApi projectScopeApi;

    public void assertCanCreate(Actor actor, Long projectId, String actionCode, String scopeCode) {
        requirePermission(actor, PERMISSION_MANAGE);
        ManagementBounds bounds = resolveBounds(actor, projectId, true);
        assertGrantFits(bounds, actionCode, scopeCode);
    }

    public ManagementBounds assertCanQuery(Actor actor, Long projectId, boolean hideDenied) {
        try {
            requirePermission(actor, PERMISSION_QUERY);
            return resolveBounds(actor, projectId, false);
        } catch (ServiceException ex) {
            if (hideDenied) {
                throw exception(PROJECT_AUTHORIZATION_NOT_FOUND);
            }
            throw ex;
        }
    }

    public void assertCanRevoke(Actor actor, AuthorizationGrantDTO grant) {
        requirePermission(actor, PERMISSION_MANAGE);
        requirePermission(actor, PERMISSION_REVOKE);
        ManagementBounds bounds = resolveBounds(actor, grant.resourceId(), true);
        assertGrantFits(bounds, grant.actionCode(), grant.scopeCode());
    }

    public void assertCanAssign(Actor actor, Long projectId) {
        resolveBounds(actor, projectId, true);
    }

    private ManagementBounds resolveBounds(Actor actor, Long projectId, boolean lockRoot) {
        requireActor(actor);
        ProjectMasterDO initial = requireProject(actor.tenantId(), projectId);
        long rootId = rootId(initial);
        if (lockRoot) {
            ProjectMasterDO lockedRoot = projectMapper.selectByIdForUpdate(rootId);
            ProjectMasterDO current = requireProject(actor.tenantId(), projectId);
            if (lockedRoot == null || !Objects.equals(rootId(current), rootId)) {
                throw exception(PROJECT_TREE_VERSION_CONFLICT);
            }
        }
        ProjectTreeVersionDO version = versionMapper.selectLatestActive(rootId);
        if (version == null) {
            throw exception(PROJECT_TREE_VERSION_CONFLICT);
        }
        requireServiceManagerRole(actor);
        ProjectScopeResult scope = projectScopeApi.resolve(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ACTION_MANAGE, version.getTreeVersion()));
        if (!scope.fullProjectIds().contains(projectId)) {
            throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        }
        Set<Long> descendants = pathMapper.selectByAncestor(
                        rootId, version.getTreeVersion(), projectId, null).stream()
                .map(ProjectTreePathDO::getDescendantProjectId)
                .collect(Collectors.toUnmodifiableSet());
        boolean managesDescendants = !descendants.isEmpty()
                && scope.fullProjectIds().containsAll(descendants);
        return new ManagementBounds(managesDescendants);
    }

    private void assertGrantFits(ManagementBounds bounds, String actionCode, String scopeCode) {
        if (!Set.of(ACTION_VIEW, ACTION_MANAGE).contains(actionCode)
                || !Set.of(SCOPE_CURRENT, SCOPE_DESCENDANTS).contains(scopeCode)) {
            throw exception(PROJECT_AUTHORIZATION_INVALID);
        }
        if (SCOPE_DESCENDANTS.equals(scopeCode) && !bounds.managesDescendants()) {
            throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        }
    }

    private void requirePermission(Actor actor, String permission) {
        requireActor(actor);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), permission)) {
            throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        }
    }

    private void requireServiceManagerRole(Actor actor) {
        boolean matches = memberMapper.selectActiveByUser(new ActiveProjectMemberQuery(
                        actor.tenantId(), actor.actorId(), LocalDateTime.now())).stream()
                .map(ProjectMemberAssignmentDO::getMemberRole)
                .anyMatch(SERVICE_MANAGER_ROLES::contains);
        if (!matches) {
            throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        }
    }

    private ProjectMasterDO requireProject(Long tenantId, Long projectId) {
        ProjectMasterDO project = projectMapper.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), tenantId)) {
            throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        }
        return project;
    }

    private void requireActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0) {
            throw exception(PROJECT_AUTHORIZATION_FORBIDDEN);
        }
    }

    private long rootId(ProjectMasterDO project) {
        return project.getRootId() == null ? project.getId() : project.getRootId();
    }

    public record Actor(Long tenantId, Long actorId) {
    }

    public record ManagementBounds(boolean managesDescendants) {
    }
}
