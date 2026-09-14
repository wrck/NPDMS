package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectTaskAdmissionService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessTaskCompletionServiceTest {
    @Test void unavailableOwnerDoesNotPreventIndependentTaskCompletion() {
        var graph = mock(ProjectRuntimeGraphMapper.class);
        var commands = mock(ProjectTaskLifecycleService.class);
        var associations = mock(ProjectTaskBusinessAssociationService.class);
        var admission = mock(ProjectTaskAdmissionService.class);
        when(admission.activateEligible(anyLong(), anyLong(), anyString())).thenReturn(new ProjectTaskAdmissionService.Result(false, false));
        var service = new ProjectBusinessTaskCompletionService(graph, commands, associations, admission);
        var first = new ProjectTaskInstanceDO(); first.setId(11L);
        var next = new ProjectTaskInstanceDO(); next.setId(12L);
        when(graph.selectTasks(any())).thenReturn(List.of(first, next));
        when(commands.completeFromBusinessResult(9L, 11L, "event")).thenThrow(new IllegalStateException("Owner unavailable"));
        when(commands.completeFromBusinessResult(9L, 12L, "event")).thenReturn(new ProjectTaskLifecycleService.AutomaticResult(true, false));
        TenantContextHolder.setTenantId(1L);
        try {
            var result = service.completeEligible(9L, "event");
            assertEquals(1, result.completed()); assertTrue(result.unknown());
            verify(commands).completeFromBusinessResult(9L, 12L, "event");
        } finally { TenantContextHolder.clear(); }
    }

    @Test void unknownAdmissionSkipsItsTaskButIndependentAdmissionStillRunsBeforeAssociation() {
        var graph = mock(ProjectRuntimeGraphMapper.class);
        var commands = mock(ProjectTaskLifecycleService.class);
        var associations = mock(ProjectTaskBusinessAssociationService.class);
        var admission = mock(ProjectTaskAdmissionService.class);
        var service = new ProjectBusinessTaskCompletionService(graph, commands, associations, admission);
        var first = new ProjectTaskInstanceDO(); first.setId(11L);
        var next = new ProjectTaskInstanceDO(); next.setId(12L);
        when(graph.selectTasks(any())).thenReturn(List.of(first, next));
        when(admission.activateEligible(9L, 11L, "event")).thenReturn(new ProjectTaskAdmissionService.Result(false, true));
        when(admission.activateEligible(9L, 12L, "event")).thenReturn(new ProjectTaskAdmissionService.Result(true, false));
        when(commands.completeFromBusinessResult(9L, 12L, "event")).thenReturn(new ProjectTaskLifecycleService.AutomaticResult(false, false));
        TenantContextHolder.setTenantId(1L);
        try {
            var result = service.completeEligible(9L, "event");
            assertEquals(1, result.activated()); assertEquals(0, result.completed()); assertTrue(result.unknown());
            verify(associations, never()).synchronize(9L, 11L, "event");
            verify(commands, never()).completeFromBusinessResult(9L, 11L, "event");
            var order = inOrder(admission, associations, commands);
            order.verify(admission).activateEligible(9L, 12L, "event");
            order.verify(associations).synchronize(9L, 12L, "event");
            order.verify(commands).completeFromBusinessResult(9L, 12L, "event");
            when(admission.activateEligible(9L, 11L, "event")).thenThrow(new IllegalStateException("admission rolled back"));
            assertEquals(1, service.completeEligible(9L, "event").activated());
        } finally { TenantContextHolder.clear(); }
    }
}
