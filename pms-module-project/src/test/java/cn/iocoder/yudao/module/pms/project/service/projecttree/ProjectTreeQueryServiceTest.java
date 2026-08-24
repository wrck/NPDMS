package cn.iocoder.yudao.module.pms.project.service.projecttree;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.projecttree.command.ProjectTreeQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTreeQueryServiceTest {
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectTreeVersionMapper versionMapper;
    @Mock ProjectTreePathMapper pathMapper;
    @Mock ProjectTreeMetrics metrics;

    private ProjectTreeQueryService service;
    private final ProjectTreeQueryService.Actor actor = new ProjectTreeQueryService.Actor(1L, 9L);

    @BeforeEach
    void setUp() {
        service = new ProjectTreeQueryService(projectMapper, versionMapper, pathMapper, metrics);
    }

    @Test
    void shouldPinCursorToSameCompleteVersionWhileNewVersionBuilds() {
        ProjectMasterDO anchor = project(1L, null, 1L, 0, null);
        ProjectMasterDO first = project(2L, 1L, 1L, 0, null);
        ProjectMasterDO second = project(3L, 1L, 1L, 1, null);
        when(projectMapper.selectById(1L)).thenReturn(anchor);
        when(versionMapper.selectLatestActive(1L)).thenReturn(version(7L, "ACTIVE"));
        when(versionMapper.selectLatest(1L)).thenReturn(version(8L, "BUILDING"));
        when(pathMapper.selectDescendantsPage(1L, 1L, 7L, 1L, true, 0, 2))
                .thenReturn(List.of(first, second));

        var firstPage = service.query(new ProjectTreeQuery(1L, ProjectTreeQuery.QueryType.CHILDREN,
                null, 1, null), actor);

        assertEquals(7L, firstPage.treeVersion());
        assertTrue(firstPage.updating());
        assertNotNull(firstPage.nextCursor());
        when(versionMapper.selectActiveVersion(1L, 7L)).thenReturn(version(7L, "ACTIVE"));
        when(pathMapper.selectDescendantsPage(1L, 1L, 7L, 1L, true, 1, 2))
                .thenReturn(List.of(second));
        var secondPage = service.query(new ProjectTreeQuery(1L, ProjectTreeQuery.QueryType.CHILDREN,
                null, 1, firstPage.nextCursor()), actor);
        assertEquals(3L, secondPage.items().getFirst().getId());
        assertFalse(secondPage.updating() && secondPage.treeVersion() != 7L);
    }

    @Test
    void shouldQueryAncestorsLocateAndBusinessLevelFromProjection() {
        ProjectMasterDO anchor = project(3L, 2L, 1L, 0, "SITE");
        ProjectMasterDO root = project(1L, null, 1L, 0, "GROUP");
        ProjectMasterDO parent = project(2L, 1L, 1L, 0, "REGION");
        when(projectMapper.selectById(3L)).thenReturn(anchor);
        when(versionMapper.selectLatestActive(1L)).thenReturn(version(7L, "ACTIVE"));
        when(versionMapper.selectLatest(1L)).thenReturn(version(7L, "ACTIVE"));
        when(pathMapper.selectPathPage(1L, 1L, 7L, 3L, false, 0, 101))
                .thenReturn(List.of(root, parent));

        var ancestors = service.query(new ProjectTreeQuery(3L, ProjectTreeQuery.QueryType.ANCESTORS,
                null, 100, null), actor);
        assertEquals(List.of(1L, 2L), ancestors.items().stream().map(ProjectMasterDO::getId).toList());

        when(pathMapper.selectPathPage(1L, 1L, 7L, 3L, true, 0, 101))
                .thenReturn(List.of(root, parent, anchor));
        var locate = service.query(new ProjectTreeQuery(3L, ProjectTreeQuery.QueryType.LOCATE,
                null, 100, null), actor);
        assertEquals(List.of(1L, 2L, 3L), locate.items().stream().map(ProjectMasterDO::getId).toList());

        when(pathMapper.selectBusinessLevelPage(1L, 1L, 7L, "SITE", 0, 101))
                .thenReturn(List.of(anchor));
        var business = service.query(new ProjectTreeQuery(3L, ProjectTreeQuery.QueryType.BUSINESS_LEVEL,
                "SITE", 100, null), actor);
        assertEquals(List.of(3L), business.items().stream().map(ProjectMasterDO::getId).toList());
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
