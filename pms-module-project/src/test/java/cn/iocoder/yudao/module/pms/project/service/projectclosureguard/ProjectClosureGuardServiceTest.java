package cn.iocoder.yudao.module.pms.project.service.projectclosureguard;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressSnapshotDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressSnapshotMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectOperationAuditService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectClosureGuardServiceTest {
    private final ProjectMasterMapper projectMapper = mock(ProjectMasterMapper.class);
    private final ProjectTreeVersionMapper treeVersionMapper = mock(ProjectTreeVersionMapper.class);
    private final ProjectTreePathMapper pathMapper = mock(ProjectTreePathMapper.class);
    private final ProjectProgressSnapshotMapper snapshotMapper = mock(ProjectProgressSnapshotMapper.class);
    private final ProjectTreeScopeService scopeService = mock(ProjectTreeScopeService.class);
    private final ClosureStatePort closureStatePort = mock(ClosureStatePort.class);
    private final ProjectOperationAuditService auditService = mock(ProjectOperationAuditService.class);
    private final ProjectClosureGuardService service = new ProjectClosureGuardService(projectMapper,
            treeVersionMapper, pathMapper, snapshotMapper, scopeService, closureStatePort, auditService);

    @BeforeEach
    void setUpTree() {
        when(projectMapper.selectById(10L)).thenReturn(project(10L, "ROOT", "根项目"));
        when(projectMapper.selectByIdForUpdate(10L)).thenReturn(project(10L, "ROOT", "根项目"));
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(4L);
        when(treeVersionMapper.selectLatestActive(10L)).thenReturn(version);
        when(pathMapper.selectByAncestor(10L, 4L, 10L, null)).thenReturn(List.of(
                path(10L, 0), path(11L, 1), path(12L, 2)));
        when(projectMapper.selectBatchIds(List.of(11L, 12L))).thenReturn(List.of(
                project(11L, "CHILD-1", "子项目一"), project(12L, "CHILD-2", "子项目二")));
    }

    @Test
    void allowsOnlyWhenAllDescendantsClosedAndRequiredSnapshotsReady() {
        allowScope(Set.of(10L, 11L, 12L));
        when(closureStatePort.findByProjectIds(0L, List.of(11L, 12L))).thenReturn(Map.of(
                11L, ClosureStatePort.ClosureState.CLOSED,
                12L, ClosureStatePort.ClosureState.CLOSED));
        when(pathMapper.selectParentsWithChildren(10L, 4L, Set.of(10L, 11L, 12L)))
                .thenReturn(Set.of(10L, 11L));
        when(snapshotMapper.selectLatestByProjects(0L, Set.of(10L, 11L)))
                .thenReturn(List.of(snapshot(10L, "READY"), snapshot(11L, "READY")));

        ProjectClosureGuardResult result = service.evaluate(10L, 4L, actor());

        assertTrue(result.allowed());
        assertTrue(result.blockers().isEmpty());
        assertTrue(result.pendingProgressProjects().isEmpty());
    }

    @Test
    void reportsExecutingAndClosureApprovingDescendants() {
        allowScope(Set.of(10L, 11L, 12L));
        when(closureStatePort.findByProjectIds(0L, List.of(11L, 12L))).thenReturn(Map.of(
                11L, ClosureStatePort.ClosureState.EXECUTING,
                12L, ClosureStatePort.ClosureState.CLOSURE_APPROVING));
        when(pathMapper.selectParentsWithChildren(10L, 4L, Set.of(10L, 11L, 12L)))
                .thenReturn(Set.of());

        ProjectClosureGuardResult result = service.evaluate(10L, 4L, actor());

        assertFalse(result.allowed());
        assertEquals(List.of("EXECUTING", "CLOSURE_APPROVING"), result.blockers().stream()
                .map(ProjectClosureGuardResult.BlockingProject::blockerType).toList());
    }

    @Test
    void missingOrPendingAggregateSnapshotBlocksClosure() {
        allowScope(Set.of(10L, 11L, 12L));
        when(closureStatePort.findByProjectIds(0L, List.of(11L, 12L))).thenReturn(Map.of(
                11L, ClosureStatePort.ClosureState.CLOSED,
                12L, ClosureStatePort.ClosureState.CLOSED));
        when(pathMapper.selectParentsWithChildren(10L, 4L, Set.of(10L, 11L, 12L)))
                .thenReturn(Set.of(10L, 11L));
        when(snapshotMapper.selectLatestByProjects(0L, Set.of(10L, 11L)))
                .thenReturn(List.of(snapshot(11L, "PENDING")));

        ProjectClosureGuardResult result = service.evaluate(10L, 4L, actor());

        assertFalse(result.allowed());
        assertEquals(List.of(10L, 11L), result.pendingProgressProjects());
    }

    @Test
    void redactsBlockerBusinessFieldsWithoutFullVisibility() {
        allowScope(Set.of(10L, 11L));
        when(closureStatePort.findByProjectIds(0L, List.of(11L, 12L))).thenReturn(Map.of(
                11L, ClosureStatePort.ClosureState.CLOSED,
                12L, ClosureStatePort.ClosureState.EXECUTING));
        when(pathMapper.selectParentsWithChildren(10L, 4L, Set.of(10L, 11L, 12L)))
                .thenReturn(Set.of());

        ProjectClosureGuardResult.BlockingProject blocker = service.evaluate(10L, 4L, actor())
                .blockers().getFirst();

        assertEquals(12L, blocker.projectId());
        assertNull(blocker.projectCode());
        assertNull(blocker.projectName());
    }

    @Test
    void rejectsStaleTreeVersion() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> service.evaluate(10L, 3L, actor()));

        assertEquals(PROJECT_TREE_VERSION_CONFLICT.getCode(), error.getCode());
    }

    private void allowScope(Set<Long> fullProjectIds) {
        when(scopeService.resolve(9L, 10L, 4L)).thenReturn(
                new ProjectTreeScopeService.ProjectTreeScope(10L, 4L, fullProjectIds, Set.of(), Set.of()));
    }

    private static ProjectClosureGuardService.Actor actor() {
        return new ProjectClosureGuardService.Actor(0L, 9L, "corr-1");
    }

    private static ProjectMasterDO project(Long id, String code, String name) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        project.setRootId(10L);
        project.setTenantId(0L);
        project.setProjectCode(code);
        project.setProjectName(name);
        return project;
    }

    private static ProjectTreePathDO path(Long descendantId, int distance) {
        ProjectTreePathDO path = new ProjectTreePathDO();
        path.setDescendantProjectId(descendantId);
        path.setDistance(distance);
        return path;
    }

    private static ProjectProgressSnapshotDO snapshot(Long projectId, String status) {
        ProjectProgressSnapshotDO snapshot = new ProjectProgressSnapshotDO();
        snapshot.setProjectId(projectId);
        snapshot.setSnapshotStatus(status);
        return snapshot;
    }
}
