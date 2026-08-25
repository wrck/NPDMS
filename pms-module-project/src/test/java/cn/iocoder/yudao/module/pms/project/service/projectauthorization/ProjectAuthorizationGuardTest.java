package cn.iocoder.yudao.module.pms.project.service.projectauthorization;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_AUTHORIZATION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAuthorizationGuardTest {

    @Mock PermissionApi permissionApi;
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock ProjectTreeVersionMapper versionMapper;
    @Mock ProjectTreePathMapper pathMapper;
    @Mock ProjectScopeApi projectScopeApi;
    private ProjectAuthorizationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ProjectAuthorizationGuard(permissionApi, projectMapper, memberMapper,
                versionMapper, pathMapper, projectScopeApi);
    }

    @Test
    void serviceManagerWithFullManageScopeCanGrantDescendants() {
        stubProjectAndTree();
        when(permissionApi.hasAnyPermissions(7L, ProjectAuthorizationGuard.PERMISSION_MANAGE)).thenReturn(true);
        when(memberMapper.selectActiveByUser(any())).thenReturn(List.of(assignment("SERVICE_MANAGER_L1")));
        when(projectScopeApi.resolve(any())).thenReturn(new ProjectScopeResult(1L, 5L,
                Set.of(1L, 2L, 3L), Set.of()));
        when(pathMapper.selectByAncestor(1L, 5L, 2L, null)).thenReturn(List.of(path(2L), path(3L)));

        guard.assertCanCreate(actor(), 2L, "PROJECT_MANAGE", "PROJECT_AND_DESCENDANTS");

        verify(projectMapper).selectByIdForUpdate(1L);
    }

    @Test
    void projectManagerRoleCannotGrantEvenWithManageScope() {
        stubProjectAndTree();
        when(permissionApi.hasAnyPermissions(7L, ProjectAuthorizationGuard.PERMISSION_MANAGE)).thenReturn(true);
        when(memberMapper.selectActiveByUser(any())).thenReturn(List.of(assignment("PROJECT_MANAGER")));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> guard.assertCanCreate(actor(), 2L, "PROJECT_VIEW", "CURRENT_PROJECT"));

        assertEquals(PROJECT_AUTHORIZATION_FORBIDDEN.getCode(), failure.getCode());
        verify(projectScopeApi, never()).resolve(any());
    }

    @Test
    void engineeringManagerWithManageScopeCanAssignServiceManagerWithoutExistingServiceManagerRole() {
        stubProjectAndTree();
        when(projectScopeApi.resolve(any())).thenReturn(new ProjectScopeResult(1L, 5L,
                Set.of(2L), Set.of(1L)));
        when(pathMapper.selectByAncestor(1L, 5L, 2L, null)).thenReturn(List.of(path(2L)));

        guard.assertCanAssign(actor(), 2L);

        verify(memberMapper, never()).selectActiveByUser(any());
    }

    @Test
    void currentOnlyManageScopeCannotGrantDescendants() {
        stubProjectAndTree();
        when(permissionApi.hasAnyPermissions(7L, ProjectAuthorizationGuard.PERMISSION_MANAGE)).thenReturn(true);
        when(memberMapper.selectActiveByUser(any())).thenReturn(List.of(assignment("SERVICE_MANAGER_L2")));
        when(projectScopeApi.resolve(any())).thenReturn(new ProjectScopeResult(1L, 5L, Set.of(2L), Set.of(1L)));
        when(pathMapper.selectByAncestor(1L, 5L, 2L, null)).thenReturn(List.of(path(2L), path(3L)));

        ServiceException failure = assertThrows(ServiceException.class,
                () -> guard.assertCanCreate(actor(), 2L, "PROJECT_VIEW", "PROJECT_AND_DESCENDANTS"));

        assertEquals(PROJECT_AUTHORIZATION_FORBIDDEN.getCode(), failure.getCode());
    }

    @Test
    void hiddenQueryReturnsNotFoundWhenPermissionIsMissing() {
        when(permissionApi.hasAnyPermissions(7L, ProjectAuthorizationGuard.PERMISSION_QUERY)).thenReturn(false);

        ServiceException failure = assertThrows(ServiceException.class,
                () -> guard.assertCanQuery(actor(), 2L, true));

        assertEquals(PROJECT_AUTHORIZATION_NOT_FOUND.getCode(), failure.getCode());
        verify(projectMapper, never()).selectById(any());
    }

    @Test
    void crossTenantProjectIsRejectedBeforeScopeResolution() {
        ProjectMasterDO project = project(2L, 1L);
        project.setTenantId(9L);
        when(permissionApi.hasAnyPermissions(7L, ProjectAuthorizationGuard.PERMISSION_MANAGE)).thenReturn(true);
        when(projectMapper.selectById(2L)).thenReturn(project);

        ServiceException failure = assertThrows(ServiceException.class,
                () -> guard.assertCanCreate(actor(), 2L, "PROJECT_VIEW", "CURRENT_PROJECT"));

        assertEquals(PROJECT_AUTHORIZATION_FORBIDDEN.getCode(), failure.getCode());
        verify(projectScopeApi, never()).resolve(any());
    }

    @Test
    void revokeRequiresBothManageAndRevokePermissions() {
        when(permissionApi.hasAnyPermissions(7L, ProjectAuthorizationGuard.PERMISSION_MANAGE)).thenReturn(true);
        when(permissionApi.hasAnyPermissions(7L, ProjectAuthorizationGuard.PERMISSION_REVOKE)).thenReturn(false);

        ServiceException failure = assertThrows(ServiceException.class,
                () -> guard.assertCanRevoke(actor(), grant()));

        assertEquals(PROJECT_AUTHORIZATION_FORBIDDEN.getCode(), failure.getCode());
        verify(projectMapper, never()).selectById(any());
    }

    private void stubProjectAndTree() {
        ProjectMasterDO project = project(2L, 1L);
        ProjectMasterDO root = project(1L, 1L);
        when(projectMapper.selectById(2L)).thenReturn(project);
        when(projectMapper.selectByIdForUpdate(1L)).thenReturn(root);
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(5L);
        when(versionMapper.selectLatestActive(1L)).thenReturn(version);
    }

    private ProjectMasterDO project(Long id, Long rootId) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        project.setRootId(rootId);
        project.setTenantId(0L);
        return project;
    }

    private ProjectMemberAssignmentDO assignment(String role) {
        ProjectMemberAssignmentDO assignment = new ProjectMemberAssignmentDO();
        assignment.setMemberRole(role);
        return assignment;
    }

    private ProjectTreePathDO path(Long projectId) {
        ProjectTreePathDO path = new ProjectTreePathDO();
        path.setDescendantProjectId(projectId);
        return path;
    }

    private AuthorizationGrantDTO grant() {
        return new AuthorizationGrantDTO(30L, 0L, "USER", 9L, "PROJ", "PROJECT", 2L,
                "PROJECT_VIEW", "CURRENT_PROJECT", null, null, "ACTIVE", "PROJ",
                "Project", "2", 7L, null, null, null, null, 0);
    }

    private ProjectAuthorizationGuard.Actor actor() {
        return new ProjectAuthorizationGuard.Actor(0L, 7L);
    }
}
