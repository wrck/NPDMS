package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessTaskCompletionServiceTest {
    @Test void unavailableOwnerDoesNotPreventIndependentTaskCompletion() {
        var graph = mock(ProjectRuntimeGraphMapper.class);
        var commands = mock(ProjectTaskLifecycleService.class);
        var associations = mock(ProjectTaskBusinessAssociationService.class);
        var service = new ProjectBusinessTaskCompletionService(graph, commands, associations);
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
}
