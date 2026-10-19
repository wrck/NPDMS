package cn.iocoder.yudao.module.pms.acceptance.service.normalclosure;

import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.normalclosure.NormalClosureMapper;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureCheckApi;
import cn.iocoder.yudao.module.pms.project.api.closure.ProjectClosureContextApi;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NormalClosureQueryServiceTest {
    private final ProjectClosureContextApi contexts = mock(ProjectClosureContextApi.class);
    private final ProjectClosureCheckApi graph = mock(ProjectClosureCheckApi.class);
    private final NormalClosureQueryService service = new NormalClosureQueryService(contexts, graph,
            mock(NormalClosureMapper.class), mock(PermissionApi.class));

    private void context() {
        when(contexts.read(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(
                new ProjectClosureContextApi.ClosureContext(9L, 7L, 0L, "ACTIVE", "S6", 1L, 9L,
                        1L, NormalClosurePolicyTest.policy(), 1L, 1L, 2L, 1));
    }

    @Test void unavailableRuntimeGraphReturnsAFailedCheckInsteadOfFailingThePage() {
        context();
        when(graph.inspectGraph(7L, 9L)).thenThrow(new IllegalStateException("temporarily unavailable"));
        var view = service.get(9L, new NormalClosureApplicationService.Actor(7L, 1L, "test"));
        assertTrue(view.checks().stream().anyMatch(check -> "RUNTIME_GRAPH".equals(check.code()) && !check.passed()));
        assertFalse(view.allowedActions().contains("SUBMIT"));
        verify(graph, times(1)).inspectGraph(7L, 9L);
    }

    @Test void allGraphChecksUseOneConsistentObservation() {
        context();
        when(graph.inspectGraph(7L, 9L)).thenReturn(new ProjectClosureCheckApi.GraphOverview(true, "SATISFIED", 10L, true));
        var view = service.get(9L, new NormalClosureApplicationService.Actor(7L, 1L, "test"));
        for (String code : List.of("TERMINAL_STAGE", "STAGE_COMPLETION", "ALL_TASKS_DONE")) {
            assertTrue(view.checks().stream().anyMatch(check -> code.equals(check.code()) && check.passed()));
        }
        verify(graph, times(1)).inspectGraph(7L, 9L);
    }
}
