package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.*;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectRuntimeCoordinator;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectChildWaitDeliveryTest {
    final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    final ProjectTreeVersionMapper versions = mock(ProjectTreeVersionMapper.class);
    final ProjectTreeHierarchyMapper paths = mock(ProjectTreeHierarchyMapper.class);
    final ProjectRuntimeCoordinator coordinator = mock(ProjectRuntimeCoordinator.class);
    final ProjectChildWaitDelivery delivery = new ProjectChildWaitDelivery(projects, versions, paths, coordinator);
    final ProjectChildWaitEvents.Changed event = new ProjectChildWaitEvents.Changed("changed", 7L, 11L, 12L, "corr");
    @BeforeEach void setup() {
        var child = ProjectChildWaitFactsTest.child(11L, "EXCEPTION_CLOSED"); child.setRootId(1L); child.setParentId(9L);
        when(projects.selectById(11L)).thenReturn(child);
        var version = new ProjectTreeVersionDO(); version.setTenantId(7L); version.setTreeVersion(8L);
        when(versions.selectLatestActive(1L)).thenReturn(version);
        when(paths.selectWaitAncestors(any())).thenReturn(List.of(ancestor(9L), ancestor(1L)));
        when(coordinator.reevaluate(anyLong(), anyLong(), anyString())).thenReturn(new ProjectRuntimeCoordinator.Result(false, 0, 0));
    }
    @Test void independentAncestorsContinueAfterFailureAndDuplicatesOnlyReevaluateCurrentPlans() {
        when(coordinator.reevaluate(9L, 12L, "corr")).thenThrow(new IllegalStateException("unavailable"));
        assertFalse(delivery.deliver(event));
        verify(coordinator).reevaluate(1L, 12L, "corr");
        doReturn(new ProjectRuntimeCoordinator.Result(false, 0, 0)).when(coordinator).reevaluate(9L, 12L, "corr");
        assertTrue(delivery.deliver(event)); assertTrue(delivery.deliver(event));
        verify(coordinator, times(3)).reevaluate(9L, 12L, "corr");
        verify(coordinator, never()).reevaluate(eq(11L), any(), any());
        verify(paths, times(3)).selectWaitAncestors(argThat(q -> q.treeVersion() == 8L && q.tenantId() == 7L));
    }
    @Test void missingProjectionCannotAcknowledgeParentWakeup() {
        when(paths.selectWaitAncestors(any())).thenReturn(List.of());
        assertFalse(delivery.deliver(event)); verifyNoInteractions(coordinator);
        when(versions.selectLatestActive(1L)).thenReturn(null);
        assertFalse(delivery.deliver(event));
    }
    static ProjectTreePathDO ancestor(Long id) {
        var path = new ProjectTreePathDO(); path.setAncestorProjectId(id); return path;
    }
}
