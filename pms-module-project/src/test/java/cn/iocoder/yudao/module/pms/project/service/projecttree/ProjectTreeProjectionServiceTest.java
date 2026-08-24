package cn.iocoder.yudao.module.pms.project.service.projecttree;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreePathDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeChangeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.platform.ProjectCommandExecutionService;
import cn.iocoder.yudao.module.pms.project.service.projecttree.command.MoveProjectSubtreeCommand;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTreeProjectionServiceTest {
    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectTreeVersionMapper versionMapper;
    @Mock ProjectTreePathMapper pathMapper;
    @Mock ProjectTreeChangeMapper changeMapper;
    @Mock ProjectCommandExecutionService commandExecutionService;
    @Mock ProjectTreeMetrics metrics;
    @Mock ProjectTreeScopeService scopeService;

    private ProjectTreeProjectionService service;

    @BeforeEach
    void setUp() {
        service = new ProjectTreeProjectionService(projectMapper, versionMapper, pathMapper,
                changeMapper, commandExecutionService, metrics, scopeService);
    }

    @Test
    void shouldPublishCompleteRootProjectionBeforeActivation() {
        ProjectMasterDO root = project(1L, null, 1L);
        ProjectMasterDO child = project(2L, 1L, 1L);
        ProjectMasterDO grandchild = project(3L, 2L, 1L);
        when(projectMapper.selectTreeByRootId(1L)).thenReturn(List.of(root, child, grandchild));
        when(projectMapper.selectByIdForUpdate(1L)).thenReturn(root);
        when(versionMapper.selectLatestActive(1L)).thenReturn(activeVersion(7L));
        when(versionMapper.insert(any(ProjectTreeVersionDO.class))).thenReturn(1);
        when(versionMapper.updateById(any(ProjectTreeVersionDO.class))).thenReturn(1);
        AtomicInteger insertedPaths = new AtomicInteger();
        when(pathMapper.insertBatch(any(), org.mockito.ArgumentMatchers.eq(1000))).thenAnswer(invocation -> {
            insertedPaths.addAndGet(invocation.<java.util.Collection<?>>getArgument(0).size());
            return true;
        });

        var result = service.publish(1L, 8L, "batch-1");

        assertEquals(3, result.nodeCount());
        assertEquals(6, result.pathCount());
        verify(pathMapper).insertBatch(any(), org.mockito.ArgumentMatchers.eq(1000));
        assertEquals(6, insertedPaths.get());
        ArgumentCaptor<ProjectTreeVersionDO> version = ArgumentCaptor.forClass(ProjectTreeVersionDO.class);
        verify(versionMapper).updateById(version.capture());
        assertEquals("ACTIVE", version.getValue().getStatus());
        assertEquals(6, version.getValue().getPathCount());
    }

    @Test
    void shouldLeaveFailedVersionWhenTruthContainsCycle() {
        ProjectMasterDO root = project(1L, null, 1L);
        ProjectMasterDO cycle = project(2L, 2L, 1L);
        when(projectMapper.selectTreeByRootId(1L)).thenReturn(List.of(root, cycle));
        when(projectMapper.selectByIdForUpdate(1L)).thenReturn(root);
        when(versionMapper.selectLatestActive(1L)).thenReturn(activeVersion(8L));
        when(versionMapper.insert(any(ProjectTreeVersionDO.class))).thenReturn(1);
        when(versionMapper.updateById(any(ProjectTreeVersionDO.class))).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> service.publish(1L, 9L, "batch-2"));

        ArgumentCaptor<ProjectTreeVersionDO> version = ArgumentCaptor.forClass(ProjectTreeVersionDO.class);
        verify(versionMapper).updateById(version.capture());
        assertEquals("FAILED", version.getValue().getStatus());
        assertEquals("PROJECT_TREE_CYCLE", version.getValue().getFailedReason());
    }

    @Test
    void shouldLockStableIdsAndPublishMoveAsOneVersionedCommand() {
        ProjectMasterDO root = project(1L, null, 1L);
        ProjectMasterDO node = project(2L, 1L, 1L);
        node.setTreePath("/1/"); node.setTreeDepth(1); node.setTenantId(1L); node.setVersion(0);
        ProjectMasterDO target = project(3L, 1L, 1L);
        target.setTreePath("/1/"); target.setTreeDepth(1); target.setTenantId(1L);
        root.setTenantId(1L);
        when(projectMapper.selectById(2L)).thenReturn(node);
        when(projectMapper.selectById(3L)).thenReturn(target);
        when(projectMapper.selectByIdsForUpdate(List.of(1L, 2L, 3L))).thenReturn(List.of(root, node, target));
        ProjectTreeVersionDO active = new ProjectTreeVersionDO();
        active.setRootProjectId(1L); active.setTreeVersion(7L); active.setStatus("ACTIVE");
        when(versionMapper.selectLatestActive(1L)).thenReturn(active);
        when(versionMapper.selectLatest(1L)).thenReturn(active);
        ProjectTreePathDO self = new ProjectTreePathDO();
        self.setAncestorProjectId(2L); self.setDescendantProjectId(2L); self.setDistance(0);
        when(pathMapper.selectByAncestor(1L, 7L, 2L, null)).thenReturn(List.of(self));
        when(projectMapper.updateById(any(ProjectMasterDO.class))).thenReturn(1);
        when(changeMapper.insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeChangeDO.class)))
                .thenReturn(1);
        doAnswer(invocation -> new ProjectCommandExecutionService.ExecutionResult<>(
                ProjectCommandExecutionService.Decision.NEW,
                invocation.<java.util.function.Supplier<?>>getArgument(3).get()))
                .when(commandExecutionService).execute(any(), anyString(),
                        eq(ProjectTreeProjectionService.MoveProjectSubtreeResult.class), any(), any());
        ProjectTreeProjectionService spy = spy(service);
        doReturn(new ProjectTreeProjectionService.ProjectionResult(1L, 8L, 3, 5))
                .when(spy).publish(eq(1L), eq(8L), anyString());

        var result = spy.move(new MoveProjectSubtreeCommand(2L, 3L, 7L, "调整",
                        "idem-move", "a".repeat(64)),
                new ProjectTreeProjectionService.Actor(1L, 9L, "corr-1"));

        assertEquals(8L, result.treeVersion());
        assertEquals(List.of(new ProjectTreeProjectionService.AffectedRootVersion(1L, 8L)),
                result.affectedRoots());
        ArgumentCaptor<ProjectMasterDO> update = ArgumentCaptor.forClass(ProjectMasterDO.class);
        verify(projectMapper).updateById(update.capture());
        assertEquals(3L, update.getValue().getParentId());
        verify(scopeService).assertFullAccess(9L, 2L, 7L);
        verify(scopeService).assertFullAccess(9L, 3L, 7L);
        verify(changeMapper).insert(any(cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeChangeDO.class));
    }

    private ProjectMasterDO project(Long id, Long parentId, Long rootId) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(id);
        project.setParentId(parentId);
        project.setRootId(rootId);
        return project;
    }

    private ProjectTreeVersionDO activeVersion(Long treeVersion) {
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setRootProjectId(1L);
        version.setTreeVersion(treeVersion);
        version.setStatus("ACTIVE");
        return version;
    }
}
