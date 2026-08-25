package cn.iocoder.yudao.module.pms.project.service.projectgovernance.provider;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceGuardQuery;
import cn.iocoder.yudao.module.pms.platform.api.guard.ProjectGovernanceProviderFact;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTreeGovernanceGuardProviderTest {

    private static final long TENANT_ID = 7L;
    private static final long ROOT_ID = 10L;
    private static final long TREE_VERSION = 12L;
    private static final LocalDateTime CHECKED_AT = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Mock
    private ProjectMasterMapper projectMapper;
    @Mock
    private ProjectTreeVersionMapper treeVersionMapper;
    @Mock
    private ProjectTreePathMapper treePathMapper;
    private ProjectTreeGovernanceGuardProvider provider;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        provider = new ProjectTreeGovernanceGuardProvider(projectMapper, treeVersionMapper, treePathMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldFreezeTreeVersionAndReturnStableActiveBlockers() {
        ProjectMasterDO active = project(12L, "P-12", "ACTIVE", 3);
        ProjectMasterDO closed = project(11L, "P-11", "NORMAL_CLOSED", 5);
        active.setTreeDepth(2);
        closed.setTreeDepth(1);
        ProjectTreeVersionDO version = activeVersion();
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(List.of(active, closed))
                .thenReturn(List.of(closed, active)).thenReturn(List.of(closed, active))
                .thenReturn(List.of(active, closed));
        when(treeVersionMapper.selectLatestActive(ROOT_ID)).thenReturn(version);
        when(treePathMapper.selectByAncestor(ROOT_ID, TREE_VERSION, ROOT_ID, null))
                .thenReturn(rootPaths(11L, 12L));
        when(treePathMapper.selectByDescendants(eq(ROOT_ID), eq(TREE_VERSION), anyCollection()))
                .thenReturn(treePaths()).thenReturn(treePaths().reversed());

        ProjectGovernanceGuardQuery query = query(Set.of(12L, 11L));
        ProjectGovernanceProviderFact first = provider.inspect(query);
        ProjectGovernanceProviderFact second = provider.inspect(query);

        assertEquals(ROOT_ID + ":" + TREE_VERSION, first.factVersion());
        assertEquals(first.factDigest(), second.factDigest());
        assertEquals(first.watermark(), second.watermark());
        assertEquals(1, first.blockers().size());
        assertEquals("ACTIVE_DESCENDANT", first.blockers().getFirst().code());
        assertEquals("12", first.blockers().getFirst().objectId());
        assertEquals("项目节点阻断", first.blockers().getFirst().summary());
    }

    @Test
    void shouldFailClosedWhenActiveTreePathIsIncomplete() {
        ProjectMasterDO closed = project(11L, "P-11", "NORMAL_CLOSED", 5);
        closed.setTreeDepth(1);
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(List.of(closed));
        ProjectTreeVersionDO version = activeVersion();
        version.setNodeCount(2);
        version.setPathCount(3);
        when(treeVersionMapper.selectLatestActive(ROOT_ID)).thenReturn(version);
        when(treePathMapper.selectByAncestor(ROOT_ID, TREE_VERSION, ROOT_ID, null))
                .thenReturn(rootPaths(11L));
        when(treePathMapper.selectByDescendants(eq(ROOT_ID), eq(TREE_VERSION), anyCollection()))
                .thenReturn(List.of(selfPath(ROOT_ID), subtreePath(ROOT_ID, 11L, 1)));

        ProjectGovernanceProviderFact fact = provider.inspect(query(Set.of(11L)));

        assertTrue(fact.blockers().stream().anyMatch(
                blocker -> "PROJECT_TREE_INCOMPLETE".equals(blocker.code())));
    }

    @Test
    void shouldExpandCurrentSubtreeAndRejectOmittedActiveDescendant() {
        ProjectMasterDO anchor = project(11L, "P-11", "ACTIVE", 5);
        ProjectMasterDO activeDescendant = project(12L, "P-12", "ACTIVE", 3);
        anchor.setTreeDepth(1);
        activeDescendant.setTreeDepth(2);
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(List.of(anchor))
                .thenReturn(List.of(activeDescendant, anchor))
                .thenReturn(List.of(anchor, activeDescendant))
                .thenReturn(List.of(activeDescendant, anchor));
        when(treeVersionMapper.selectLatestActive(ROOT_ID)).thenReturn(activeVersion());
        when(treePathMapper.selectByAncestor(ROOT_ID, TREE_VERSION, ROOT_ID, null))
                .thenReturn(rootPaths(11L, 12L));
        when(treePathMapper.selectByDescendants(eq(ROOT_ID), eq(TREE_VERSION), anyCollection()))
                .thenReturn(treePaths());

        ProjectGovernanceProviderFact incomplete = provider.inspect(query(Set.of(11L)));
        ProjectGovernanceProviderFact complete = provider.inspect(query(Set.of(11L, 12L)));

        assertTrue(incomplete.blockers().stream().anyMatch(
                blocker -> "PROJECT_TREE_SCOPE_INCOMPLETE".equals(blocker.code())));
        assertTrue(incomplete.blockers().stream().anyMatch(
                blocker -> "ACTIVE_DESCENDANT".equals(blocker.code()) && "12".equals(blocker.objectId())));
        assertTrue(!incomplete.factDigest().equals(complete.factDigest()));
    }

    @Test
    void shouldFailClosedWhenProjectsBelongToDifferentTrees() {
        ProjectMasterDO first = project(11L, "P-11", "NORMAL_CLOSED", 5);
        ProjectMasterDO second = project(21L, "P-21", "NORMAL_CLOSED", 5);
        first.setTreeDepth(1);
        second.setTreeDepth(0);
        second.setRootId(20L);
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(List.of(first, second),
                List.of(first), List.of(second));
        ProjectTreeVersionDO firstVersion = activeVersion();
        firstVersion.setNodeCount(2);
        firstVersion.setPathCount(3);
        when(treeVersionMapper.selectLatestActive(ROOT_ID)).thenReturn(firstVersion);
        ProjectTreeVersionDO otherVersion = activeVersion();
        otherVersion.setRootProjectId(20L);
        otherVersion.setNodeCount(2);
        otherVersion.setPathCount(3);
        when(treeVersionMapper.selectLatestActive(20L)).thenReturn(otherVersion);
        when(treePathMapper.selectByAncestor(ROOT_ID, TREE_VERSION, ROOT_ID, null))
                .thenReturn(rootPaths(11L));
        when(treePathMapper.selectByAncestor(20L, TREE_VERSION, 20L, null))
                .thenReturn(List.of(otherSubtreePath(20L, 20L, 0), otherSubtreePath(20L, 21L, 1)));
        when(treePathMapper.selectByDescendants(eq(ROOT_ID), eq(TREE_VERSION), anyCollection()))
                .thenReturn(List.of(selfPath(ROOT_ID), subtreePath(ROOT_ID, 11L, 1), selfPath(11L)));
        when(treePathMapper.selectByDescendants(eq(20L), eq(TREE_VERSION), anyCollection()))
                .thenReturn(List.of(otherSubtreePath(20L, 20L, 0),
                        otherSubtreePath(20L, 21L, 1), otherSubtreePath(21L, 21L, 0)));

        ProjectGovernanceProviderFact fact = provider.inspect(query(Set.of(11L, 21L)));

        assertTrue(fact.blockers().stream().anyMatch(
                blocker -> "PROJECT_TREE_ROOT_MISMATCH".equals(blocker.code())));
    }

    @Test
    void shouldShortCircuitEmptyProjectsAndRejectCrossTenantQuery() {
        ProjectGovernanceProviderFact empty = provider.inspect(query(Set.of()));

        assertEquals("EMPTY", empty.factVersion());
        assertTrue(empty.blockers().isEmpty());
        verifyNoInteractions(projectMapper, treeVersionMapper, treePathMapper);
        assertThrows(IllegalArgumentException.class, () -> provider.inspect(
                new ProjectGovernanceGuardQuery(8L, Set.of(11L), "CLOSE", CHECKED_AT)));
    }

    @Test
    void shouldRejectOutOfScopeFactsReturnedByPersistence() {
        ProjectMasterDO crossTenant = project(11L, "P-11", "NORMAL_CLOSED", 5);
        crossTenant.setTenantId(8L);
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(List.of(crossTenant));

        assertThrows(IllegalStateException.class, () -> provider.inspect(query(Set.of(11L))));
    }

    @Test
    void shouldChangeFrozenFactWhenActiveTreeVersionChanges() {
        ProjectMasterDO anchor = project(11L, "P-11", "ACTIVE", 5);
        anchor.setTreeDepth(1);
        when(projectMapper.selectBatchIds(anyCollection())).thenReturn(List.of(anchor));
        ProjectTreeVersionDO nextVersion = activeVersion();
        ProjectTreeVersionDO currentVersion = activeVersion();
        currentVersion.setNodeCount(2);
        currentVersion.setPathCount(3);
        nextVersion.setTreeVersion(TREE_VERSION + 1);
        nextVersion.setVersion(3);
        nextVersion.setNodeCount(2);
        nextVersion.setPathCount(3);
        when(treeVersionMapper.selectLatestActive(ROOT_ID)).thenReturn(currentVersion, nextVersion);
        when(treePathMapper.selectByAncestor(ROOT_ID, TREE_VERSION, ROOT_ID, null))
                .thenReturn(rootPaths(11L));
        when(treePathMapper.selectByAncestor(ROOT_ID, TREE_VERSION + 1, ROOT_ID, null))
                .thenReturn(pathsForVersion(rootPaths(11L), TREE_VERSION + 1));
        when(treePathMapper.selectByDescendants(eq(ROOT_ID), eq(TREE_VERSION), anyCollection()))
                .thenReturn(List.of(selfPath(ROOT_ID), subtreePath(ROOT_ID, 11L, 1), selfPath(11L)));
        when(treePathMapper.selectByDescendants(eq(ROOT_ID), eq(TREE_VERSION + 1), anyCollection()))
                .thenReturn(pathsForVersion(
                        List.of(selfPath(ROOT_ID), subtreePath(ROOT_ID, 11L, 1), selfPath(11L)),
                        TREE_VERSION + 1));

        ProjectGovernanceProviderFact first = provider.inspect(query(Set.of(11L)));
        ProjectGovernanceProviderFact second = provider.inspect(query(Set.of(11L)));

        assertTrue(!first.factVersion().equals(second.factVersion()));
        assertTrue(!first.factDigest().equals(second.factDigest()));
    }

    private static ProjectGovernanceGuardQuery query(Set<Long> projectIds) {
        return new ProjectGovernanceGuardQuery(TENANT_ID, projectIds, "CLOSE", CHECKED_AT);
    }

    private static ProjectMasterDO project(Long id, String code, String lifecycleStatus, Integer version) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setTenantId(TENANT_ID);
        project.setId(id);
        project.setRootId(ROOT_ID);
        project.setProjectCode(code);
        project.setLifecycleStatus(lifecycleStatus);
        project.setVersion(version);
        project.setUpdateTime(CHECKED_AT.plusMinutes(id));
        return project;
    }

    private static ProjectTreeVersionDO activeVersion() {
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTenantId(TENANT_ID);
        version.setRootProjectId(ROOT_ID);
        version.setTreeVersion(TREE_VERSION);
        version.setNodeCount(3);
        version.setPathCount(6);
        version.setVersion(2);
        version.setActivatedAt(CHECKED_AT.minusHours(1));
        version.setUpdateTime(CHECKED_AT.minusHours(1));
        return version;
    }

    private static ProjectTreePathDO selfPath(Long projectId) {
        return subtreePath(projectId, projectId, 0);
    }

    private static ProjectTreePathDO subtreePath(Long ancestorId, Long descendantId, int distance) {
        ProjectTreePathDO path = new ProjectTreePathDO();
        path.setTenantId(TENANT_ID);
        path.setRootProjectId(ROOT_ID);
        path.setTreeVersion(TREE_VERSION);
        path.setAncestorProjectId(ancestorId);
        path.setDescendantProjectId(descendantId);
        path.setDistance(distance);
        return path;
    }

    private static ProjectTreePathDO otherSubtreePath(Long ancestorId, Long descendantId, int distance) {
        ProjectTreePathDO path = subtreePath(ancestorId, descendantId, distance);
        path.setRootProjectId(20L);
        return path;
    }

    private static List<ProjectTreePathDO> rootPaths(Long... descendantIds) {
        java.util.ArrayList<ProjectTreePathDO> paths = new java.util.ArrayList<>();
        paths.add(subtreePath(ROOT_ID, ROOT_ID, 0));
        for (int i = 0; i < descendantIds.length; i++) {
            paths.add(subtreePath(ROOT_ID, descendantIds[i], i + 1));
        }
        return List.copyOf(paths);
    }

    private static List<ProjectTreePathDO> treePaths() {
        return List.of(selfPath(ROOT_ID), subtreePath(ROOT_ID, 11L, 1),
                subtreePath(ROOT_ID, 12L, 2), selfPath(11L),
                subtreePath(11L, 12L, 1), selfPath(12L));
    }

    private static List<ProjectTreePathDO> pathsForVersion(List<ProjectTreePathDO> source, long treeVersion) {
        return source.stream().map(path -> {
            ProjectTreePathDO copy = subtreePath(path.getAncestorProjectId(),
                    path.getDescendantProjectId(), path.getDistance());
            copy.setTreeVersion(treeVersion);
            return copy;
        }).toList();
    }
}
