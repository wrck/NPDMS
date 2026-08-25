package cn.iocoder.yudao.module.pms.project.service.projectscope;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.authorization.AuthorizationGrantApi;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantDTO;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageQuery;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantPageResult;
import cn.iocoder.yudao.module.pms.platform.api.authorization.dto.AuthorizationGrantQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ActiveProjectMemberQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CreatedProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProjectTreeScopeServiceTest {

    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock ProjectTreePathMapper pathMapper;
    @Mock ProjectTreeVersionMapper versionMapper;
    @Mock AuthorizationGrantApi authorizationGrantApi;
    private ProjectTreeScopeService service;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(0L);
        service = new ProjectTreeScopeService(
                projectMapper, memberMapper, pathMapper, versionMapper, authorizationGrantApi);
        lenient().when(projectMapper.selectListCreatedBy(any(CreatedProjectScopeQuery.class)))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void managerRoleDoesNotExpandDescendantsWithoutExplicitGrant() {
        stubRootProjection();
        when(memberMapper.selectActiveByUser(any(ActiveProjectMemberQuery.class)))
                .thenReturn(List.of(assignment(2L, "PROJECT_MANAGER")));
        when(pathMapper.selectByDescendants(1L, 7L, Set.of(2L)))
                .thenReturn(List.of(path(1L, 2L), path(2L, 2L)));

        var scope = service.resolve(query("PROJECT_VIEW"));

        assertEquals(ProjectTreeScopeService.Visibility.PATH_PLACEHOLDER, scope.visibility(1L));
        assertEquals(ProjectTreeScopeService.Visibility.FULL, scope.visibility(2L));
        assertEquals(ProjectTreeScopeService.Visibility.NONE, scope.visibility(3L));
        assertEquals(ProjectTreeScopeService.Visibility.NONE, scope.visibility(4L));
    }

    @Test
    void explicitDescendantGrantExpandsOnlyItsCurrentTreeSubtree() {
        stubRootProjection();
        when(authorizationGrantApi.listEffective(any(AuthorizationGrantQuery.class)))
                .thenAnswer(invocation -> {
                    AuthorizationGrantQuery query = invocation.getArgument(0);
                    return "PROJECT_VIEW".equals(query.actionCode())
                            ? List.of(grant(2L, "PROJECT_VIEW", "PROJECT_AND_DESCENDANTS"))
                            : List.of();
                });
        when(pathMapper.selectByAncestors(1L, 7L, Set.of(2L)))
                .thenReturn(List.of(path(2L, 2L), path(2L, 3L)));
        when(pathMapper.selectByDescendants(1L, 7L, Set.of(2L, 3L)))
                .thenReturn(List.of(path(1L, 2L), path(2L, 2L), path(1L, 3L), path(2L, 3L)));

        var scope = service.resolve(query("PROJECT_VIEW"));

        assertEquals(Set.of(2L, 3L), scope.fullProjectIds());
        assertEquals(Set.of(1L), scope.placeholderProjectIds());
        assertEquals(ProjectTreeScopeService.Visibility.NONE, scope.visibility(4L));
    }

    @Test
    void manageGrantAlsoProvidesViewForSameScope() {
        stubRootProjection();
        when(authorizationGrantApi.listEffective(any(AuthorizationGrantQuery.class)))
                .thenAnswer(invocation -> {
                    AuthorizationGrantQuery query = invocation.getArgument(0);
                    return "PROJECT_MANAGE".equals(query.actionCode())
                            ? List.of(grant(3L, "PROJECT_MANAGE", "CURRENT_PROJECT"))
                            : List.of();
                });
        when(pathMapper.selectByDescendants(1L, 7L, Set.of(3L)))
                .thenReturn(List.of(path(1L, 3L), path(2L, 3L), path(3L, 3L)));

        var scope = service.resolve(query("PROJECT_VIEW"));

        assertEquals(Set.of(3L), scope.fullProjectIds());
        assertEquals(Set.of(1L, 2L), scope.placeholderProjectIds());
    }

    @Test
    void ordinaryMemberCannotManageCurrentProject() {
        stubRootProjection();
        when(memberMapper.selectActiveByUser(any(ActiveProjectMemberQuery.class)))
                .thenReturn(List.of(assignment(3L, "ENGINEER")));

        var scope = service.resolve(query("PROJECT_MANAGE"));

        assertEquals(Set.of(), scope.fullProjectIds());
        assertEquals(Set.of(), scope.placeholderProjectIds());
    }

    @Test
    void creatorCanViewOwnProjectWithoutReceivingManagerRole() {
        stubRootProjection();
        when(projectMapper.selectListCreatedBy(any(CreatedProjectScopeQuery.class)))
                .thenReturn(List.of(project(3L, 1L)));
        when(pathMapper.selectByDescendants(1L, 7L, Set.of(3L)))
                .thenReturn(List.of(path(1L, 3L), path(2L, 3L), path(3L, 3L)));

        var scope = service.resolve(query("PROJECT_VIEW"));

        assertEquals(Set.of(3L), scope.fullProjectIds());
        assertEquals(Set.of(1L, 2L), scope.placeholderProjectIds());
        verify(memberMapper).selectActiveByUser(any(ActiveProjectMemberQuery.class));
    }

    @Test
    void creatorDoesNotReceiveManageScope() {
        stubRootProjection();

        var scope = service.resolve(query("PROJECT_MANAGE"));

        assertEquals(Set.of(), scope.fullProjectIds());
        verify(projectMapper, never()).selectListCreatedBy(any(CreatedProjectScopeQuery.class));
    }

    @Test
    void rejectsAnchorFromDifferentTenant() {
        ProjectMasterDO anchor = new ProjectMasterDO();
        anchor.setId(3L);
        anchor.setRootId(1L);
        anchor.setTenantId(1L);
        when(projectMapper.selectById(3L)).thenReturn(anchor);

        assertThrows(ServiceException.class, () -> service.resolve(query("PROJECT_VIEW")));
    }

    @Test
    void memberQueryCarriesTenantUserAndEffectivePoint() {
        stubRootProjection();

        service.resolve(query("PROJECT_VIEW"));

        ArgumentCaptor<ActiveProjectMemberQuery> captor =
                ArgumentCaptor.forClass(ActiveProjectMemberQuery.class);
        verify(memberMapper).selectActiveByUser(captor.capture());
        assertEquals(0L, captor.getValue().tenantId());
        assertEquals(9L, captor.getValue().userId());
    }

    @Test
    void globalScopeReturnsEmptyWithoutMemberOrGrantAnchors() {
        when(memberMapper.selectActiveByUser(any(ActiveProjectMemberQuery.class))).thenReturn(List.of());
        when(authorizationGrantApi.page(any(AuthorizationGrantPageQuery.class)))
                .thenReturn(new AuthorizationGrantPageResult(List.of(), 0));

        assertEquals(Set.of(), service.resolveAllFullProjectIds(0L, 9L, "PROJECT_VIEW"));

        verify(projectMapper, never()).selectBatchIds(any());
    }

    @Test
    void globalScopeDiscoversGrantOnlyTreeAndReusesUnifiedResolution() {
        when(memberMapper.selectActiveByUser(any(ActiveProjectMemberQuery.class))).thenReturn(List.of());
        when(authorizationGrantApi.page(any(AuthorizationGrantPageQuery.class))).thenAnswer(invocation -> {
            AuthorizationGrantPageQuery query = invocation.getArgument(0);
            return "PROJECT_VIEW".equals(query.actionCode())
                    ? new AuthorizationGrantPageResult(
                            List.of(grant(3L, "PROJECT_VIEW", "CURRENT_PROJECT")), 1)
                    : new AuthorizationGrantPageResult(List.of(), 0);
        });
        ProjectMasterDO granted = project(3L, 1L);
        when(projectMapper.selectBatchIds(Set.of(3L))).thenReturn(List.of(granted));
        when(projectMapper.selectById(1L)).thenReturn(project(1L, 1L));
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(7L);
        when(versionMapper.selectLatestActive(1L)).thenReturn(version);
        when(pathMapper.selectByAncestor(1L, 7L, 1L, null)).thenReturn(List.of(
                path(1L, 1L), path(1L, 2L), path(1L, 3L)));
        when(authorizationGrantApi.listEffective(any(AuthorizationGrantQuery.class))).thenAnswer(invocation -> {
            AuthorizationGrantQuery query = invocation.getArgument(0);
            return "PROJECT_VIEW".equals(query.actionCode())
                    ? List.of(grant(3L, "PROJECT_VIEW", "CURRENT_PROJECT")) : List.of();
        });
        when(pathMapper.selectByDescendants(1L, 7L, Set.of(3L)))
                .thenReturn(List.of(path(1L, 3L), path(3L, 3L)));

        assertEquals(Set.of(3L), service.resolveAllFullProjectIds(0L, 9L, "PROJECT_VIEW"));
    }

    @Test
    void sanitizerReturnsOnlyStableIdForPathPlaceholder() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(4L);
        project.setProjectName("不可泄露名称");
        project.setCustomerName("敏感客户");

        var view = new ProjectTreeViewSanitizer().sanitize(
                project, ProjectTreeScopeService.Visibility.PATH_PLACEHOLDER);

        assertEquals(4L, view.projectId());
        assertNull(view.projectName());
        assertNull(view.lifecycleStatus());
        assertNull(view.currentStage());
        assertNull(view.milestoneProgress());
    }

    private void stubRootProjection() {
        ProjectMasterDO anchor = new ProjectMasterDO();
        anchor.setId(3L);
        anchor.setRootId(1L);
        anchor.setTenantId(0L);
        when(projectMapper.selectById(3L)).thenReturn(anchor);
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(7L);
        when(versionMapper.selectLatestActive(1L)).thenReturn(version);
        when(pathMapper.selectByAncestor(1L, 7L, 1L, null)).thenReturn(List.of(
                path(1L, 1L), path(1L, 2L), path(1L, 3L), path(1L, 4L)));
    }

    private ProjectScopeQuery query(String actionCode) {
        return new ProjectScopeQuery(0L, 9L, 3L, actionCode, 7L);
    }

    private ProjectMemberAssignmentDO assignment(Long projectId, String role) {
        ProjectMemberAssignmentDO value = new ProjectMemberAssignmentDO();
        value.setProjectId(projectId);
        value.setMemberRole(role);
        return value;
    }

    private ProjectMasterDO project(Long id, Long rootId) {
        ProjectMasterDO value = new ProjectMasterDO();
        value.setId(id);
        value.setRootId(rootId);
        value.setTenantId(0L);
        return value;
    }

    private AuthorizationGrantDTO grant(Long resourceId, String actionCode, String scopeCode) {
        return new AuthorizationGrantDTO(
                100L + resourceId, 0L, "USER", 9L, "PROJ", "PROJECT", resourceId,
                actionCode, scopeCode, null, null, "ACTIVE", "PROJ", null, null,
                7L, null, null, null, null, 0);
    }

    private ProjectTreePathDO path(Long ancestorId, Long descendantId) {
        ProjectTreePathDO value = new ProjectTreePathDO();
        value.setAncestorProjectId(ancestorId);
        value.setDescendantProjectId(descendantId);
        return value;
    }
}
