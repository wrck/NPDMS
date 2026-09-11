package cn.iocoder.yudao.module.pms.project.service.projecttree;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.projecttree.command.ProjectTreeQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeViewSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class ProjectTreeQueryServiceTest {
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectTreeVersionMapper versionMapper;
    @Mock ProjectTreePathMapper pathMapper;
    @Mock ProjectTreeMetrics metrics;
    @Mock ProjectTreeScopeService scopeService;
    @Mock cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeHierarchyMapper hierarchyMapper;
    @Mock cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeProgressMapper progressMapper;

    private ProjectTreeQueryService service;
    private final ProjectTreeQueryService.Actor actor = new ProjectTreeQueryService.Actor(1L, 9L);

    @BeforeEach
    void setUp() {
        service = new ProjectTreeQueryService(projectMapper, versionMapper, pathMapper, metrics,
                scopeService, new ProjectTreeViewSanitizer(), hierarchyMapper, progressMapper);
    }

    @Test
    void shouldPinCursorToSameCompleteVersionWhileNewVersionBuilds() {
        stubFullScope();
        ProjectMasterDO anchor = project(1L, null, 1L, 0, null);
        ProjectMasterDO first = project(2L, 1L, 1L, 0, null);
        ProjectMasterDO second = project(3L, 1L, 1L, 1, null);
        when(projectMapper.selectById(1L)).thenReturn(anchor);
        when(versionMapper.selectLatestActive(1L)).thenReturn(version(7L, "ACTIVE"));
        when(versionMapper.selectLatest(1L)).thenReturn(version(8L, "BUILDING"));
        when(pathMapper.selectDescendantsPage(1L, 1L, 7L, 1L, true, Set.of(1L, 2L, 3L), 0, 2))
                .thenReturn(List.of(first, second));

        var firstPage = service.query(new ProjectTreeQuery(1L, ProjectTreeQuery.QueryType.CHILDREN,
                null, 1, null), actor);

        assertEquals(7L, firstPage.treeVersion());
        assertTrue(firstPage.updating());
        assertNotNull(firstPage.nextCursor());
        when(versionMapper.selectActiveVersion(1L, 7L)).thenReturn(version(7L, "ACTIVE"));
        when(pathMapper.selectDescendantsPage(1L, 1L, 7L, 1L, true, Set.of(1L, 2L, 3L), 1, 2))
                .thenReturn(List.of(second));
        var secondPage = service.query(new ProjectTreeQuery(1L, ProjectTreeQuery.QueryType.CHILDREN,
                null, 1, firstPage.nextCursor()), actor);
        assertEquals(3L, secondPage.items().getFirst().projectId());
        assertFalse(secondPage.updating() && secondPage.treeVersion() != 7L);
    }

    @Test
    void shouldQueryAncestorsLocateAndBusinessLevelFromProjection() {
        stubFullScope();
        ProjectMasterDO anchor = project(3L, 2L, 1L, 0, "SITE");
        ProjectMasterDO root = project(1L, null, 1L, 0, "GROUP");
        ProjectMasterDO parent = project(2L, 1L, 1L, 0, "REGION");
        when(projectMapper.selectById(3L)).thenReturn(anchor);
        when(versionMapper.selectLatestActive(1L)).thenReturn(version(7L, "ACTIVE"));
        when(versionMapper.selectLatest(1L)).thenReturn(version(7L, "ACTIVE"));
        when(pathMapper.selectPathPage(1L, 1L, 7L, 3L, false, Set.of(1L, 2L, 3L), 0, 101))
                .thenReturn(List.of(root, parent));

        var ancestors = service.query(new ProjectTreeQuery(3L, ProjectTreeQuery.QueryType.ANCESTORS,
                null, 100, null), actor);
        assertEquals(List.of(1L, 2L), ancestors.items().stream().map(
                ProjectTreeViewSanitizer.ProjectTreeNodeView::projectId).toList());

        when(pathMapper.selectPathPage(1L, 1L, 7L, 3L, true, Set.of(1L, 2L, 3L), 0, 101))
                .thenReturn(List.of(root, parent, anchor));
        var locate = service.query(new ProjectTreeQuery(3L, ProjectTreeQuery.QueryType.LOCATE,
                null, 100, null), actor);
        assertEquals(List.of(1L, 2L, 3L), locate.items().stream().map(
                ProjectTreeViewSanitizer.ProjectTreeNodeView::projectId).toList());

        when(pathMapper.selectBusinessLevelPage(1L, 1L, 7L, "SITE", Set.of(1L, 2L, 3L), 0, 101))
                .thenReturn(List.of(anchor));
        var business = service.query(new ProjectTreeQuery(3L, ProjectTreeQuery.QueryType.BUSINESS_LEVEL,
                "SITE", 100, null), actor);
        assertEquals(List.of(3L), business.items().stream().map(
                ProjectTreeViewSanitizer.ProjectTreeNodeView::projectId).toList());
    }

    @Test
    void rejectsCrossTenantAnchorBeforeResolvingScope() {
        ProjectMasterDO anchor = project(1L, null, 1L, 0, null);
        anchor.setTenantId(2L);
        when(projectMapper.selectById(1L)).thenReturn(anchor);

        assertThrows(ServiceException.class, () -> service.query(
                new ProjectTreeQuery(1L, ProjectTreeQuery.QueryType.CHILDREN, null, 10, null), actor));

        verify(scopeService, never()).resolve(any(ProjectScopeQuery.class));
    }

    @Test
    void returnsVersionPinnedParentsAndDoesNotExposePlaceholderBusinessFields() {
        var anchor = project(1L, null, 1L, 0, null);
        var child = project(3L, 99L, 1L, 0, null); // Current truth has moved; cursor projection must win.
        child.setProjectName("不可泄露"); child.setProjectCode("SECRET");
        when(projectMapper.selectById(1L)).thenReturn(anchor);
        when(versionMapper.selectLatestActive(1L)).thenReturn(version(7L, "ACTIVE"));
        when(versionMapper.selectLatest(1L)).thenReturn(version(7L, "ACTIVE"));
        when(scopeService.resolve(any(ProjectScopeQuery.class))).thenReturn(
                new ProjectTreeScopeService.ProjectTreeScope(1L, 7L, Set.of(1L, 2L), Set.of(3L), Set.of()));
        when(pathMapper.selectDescendantsPage(1L, 1L, 7L, 1L, false, Set.of(1L, 2L, 3L), 0, 21)).thenReturn(List.of(child));
        var path = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO();
        path.setDescendantProjectId(3L); path.setAncestorProjectId(2L);
        when(hierarchyMapper.selectDirectParents(new cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.query.ProjectTreeParentsQuery(
                1L, 1L, 7L, Set.of(3L)))).thenReturn(List.of(path));
        var result = service.query(new ProjectTreeQuery(1L, ProjectTreeQuery.QueryType.DESCENDANTS, null, 20, null), actor);
        assertEquals(2L, result.items().getFirst().parentId());
        org.junit.jupiter.api.Assertions.assertNull(result.items().getFirst().projectName());
        org.junit.jupiter.api.Assertions.assertNull(result.items().getFirst().projectCode());
        org.junit.jupiter.api.Assertions.assertNull(result.items().getFirst().projectProgress());
        verifyNoInteractions(progressMapper);
    }

    @Test
    void httpResponseMappingPreservesTreeStructureAndProjectCode() {
        var project = project(3L, 99L, 1L, 0, null);
        project.setProjectCode("P-003"); project.setProjectName("项目");
        var view = new ProjectTreeViewSanitizer().sanitize(project, ProjectTreeScopeService.Visibility.FULL, 2L)
                .withProgress(new cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeProgressRow(
                        3L, new java.math.BigDecimal("37.5"), "READY", java.time.LocalDateTime.of(2026, 9, 11, 12, 0)));
        var response = cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(view,
                cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectTreeQueryRespVO.Node.class);
        assertEquals(2L, response.getParentId());
        assertEquals("P-003", response.getProjectCode());
        assertEquals(new java.math.BigDecimal("37.5"), response.getProjectProgress());
        assertEquals("READY", response.getProgressStatus());
        assertEquals(java.time.LocalDateTime.of(2026, 9, 11, 12, 0), response.getProgressRecordedAt());
    }

    @Test
    void rejectsStaleCursorBeforeResolvingScope() {
        when(projectMapper.selectById(1L)).thenReturn(project(1L, null, 1L, 0, null));
        String staleCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "1:7:1:CHILDREN:1:".getBytes(StandardCharsets.UTF_8));

        assertThrows(ServiceException.class, () -> service.query(
                new ProjectTreeQuery(1L, ProjectTreeQuery.QueryType.CHILDREN, null, 10, staleCursor), actor));

        verify(scopeService, never()).resolve(any(ProjectScopeQuery.class));
    }

    @Test
    void rejectsInvisibleAnchorBeforeReadingTreePage() {
        when(projectMapper.selectById(1L)).thenReturn(project(1L, null, 1L, 0, null));
        when(versionMapper.selectLatestActive(1L)).thenReturn(version(7L, "ACTIVE"));
        when(versionMapper.selectLatest(1L)).thenReturn(version(7L, "ACTIVE"));
        when(scopeService.resolve(new ProjectScopeQuery(1L, 9L, 1L, "PROJECT_VIEW", 7L))).thenReturn(
                new ProjectTreeScopeService.ProjectTreeScope(1L, 7L, Set.of(), Set.of(), Set.of()));

        assertThrows(ServiceException.class, () -> service.query(
                new ProjectTreeQuery(1L, ProjectTreeQuery.QueryType.CHILDREN, null, 10, null), actor));

        verifyNoInteractions(pathMapper);
    }

    private void stubFullScope() {
        when(scopeService.resolve(any(ProjectScopeQuery.class))).thenReturn(
                new ProjectTreeScopeService.ProjectTreeScope(1L, 7L,
                        Set.of(1L, 2L, 3L), Set.of(), Set.of()));
    }

    private ProjectMasterDO project(Long id, Long parentId, Long rootId, int sort, String level) {
        ProjectMasterDO value = new ProjectMasterDO();
        value.setId(id); value.setParentId(parentId); value.setRootId(rootId); value.setTenantId(1L);
        value.setTreeDepth(parentId == null ? 0 : 1); value.setTreeSort(sort); value.setBusinessLevelCode(level);
        return value;
    }

    private ProjectTreeVersionDO version(Long treeVersion, String status) {
        ProjectTreeVersionDO value = new ProjectTreeVersionDO();
        value.setRootProjectId(1L); value.setTreeVersion(treeVersion); value.setStatus(status);
        return value;
    }

}
