package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectAncestorResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAncestorQueryApiImplTest {

    @Mock private ProjectMasterMapper projectMapper;
    @Mock private ProjectTreeVersionMapper treeVersionMapper;
    @Mock private ProjectTreePathMapper pathMapper;

    private ProjectAncestorQueryApiImpl api;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        api = new ProjectAncestorQueryApiImpl(projectMapper, treeVersionMapper, pathMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnRootToParentAncestorsForRequestedTreeVersion() {
        when(projectMapper.selectById(30L)).thenReturn(project(30L, 10L, 1L));
        when(treeVersionMapper.selectActiveVersion(10L, 7L)).thenReturn(activeVersion(7L));
        when(pathMapper.selectByDescendants(10L, 7L, List.of(30L))).thenReturn(List.of(
                path(20L, 30L, 1), path(30L, 30L, 0), path(10L, 30L, 2)));

        ProjectAncestorResult result = api.getAncestors(new ProjectAncestorQuery(1L, 30L, 7L));

        assertEquals(10L, result.rootProjectId());
        assertEquals(7L, result.treeVersion());
        assertEquals(List.of(10L, 20L), result.ancestorProjectIds());
    }

    @Test
    void shouldResolveLatestActiveTreeVersionWhenNotSpecified() {
        when(projectMapper.selectById(30L)).thenReturn(project(30L, 10L, 1L));
        when(treeVersionMapper.selectLatestActive(10L)).thenReturn(activeVersion(8L));
        when(pathMapper.selectByDescendants(10L, 8L, List.of(30L))).thenReturn(List.of(
                path(30L, 30L, 0), path(10L, 30L, 1)));

        ProjectAncestorResult result = api.getAncestors(new ProjectAncestorQuery(1L, 30L, null));

        assertEquals(8L, result.treeVersion());
        assertEquals(List.of(10L), result.ancestorProjectIds());
    }

    @Test
    void shouldRejectUnavailableTreeVersion() {
        when(projectMapper.selectById(30L)).thenReturn(project(30L, 10L, 1L));
        when(treeVersionMapper.selectActiveVersion(10L, 6L)).thenReturn(null);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> api.getAncestors(new ProjectAncestorQuery(1L, 30L, 6L)));

        assertEquals("TREE_VERSION_UNAVAILABLE", failure.getMessage());
    }

    private ProjectMasterDO project(Long id, Long rootId, Long tenantId) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        project.setRootId(rootId);
        project.setTenantId(tenantId);
        return project;
    }

    private ProjectTreeVersionDO activeVersion(Long treeVersion) {
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(treeVersion);
        version.setStatus("ACTIVE");
        return version;
    }

    private ProjectTreePathDO path(Long ancestorId, Long descendantId, Integer distance) {
        ProjectTreePathDO path = new ProjectTreePathDO();
        path.setAncestorProjectId(ancestorId);
        path.setDescendantProjectId(descendantId);
        path.setDistance(distance);
        return path;
    }
}
