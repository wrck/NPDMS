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
    private final NormalClosureMapper mapper = mock(NormalClosureMapper.class);
    private final PermissionApi permissions = mock(PermissionApi.class);
    private final NormalClosureQueryService service = new NormalClosureQueryService(contexts, graph,
            mapper, permissions, query -> java.util.Optional.empty());

    @Test void reopenedProjectRequiresNewCheckWithoutChangingApprovedHistory() {
        context();
        var opened = java.time.LocalDateTime.of(2026, 1, 1, 12, 0);
        var application = new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.normalclosure.NormalClosureApplicationDO();
        application.setStatus("APPROVED");
        application.setSubmittedAt(opened.minusDays(1));
        var snapshot = new cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.normalclosure.NormalClosureSnapshotDO();
        snapshot.setCheckedAt(opened.minusDays(1));
        snapshot.setPassed(true);
        when(mapper.selectLatestApplication(any())).thenReturn(application);
        when(mapper.selectLatestSnapshot(any())).thenReturn(snapshot);
        when(permissions.hasAnyPermissions(anyLong(), anyString())).thenReturn(true);
        when(graph.inspectGraph(7L, 9L)).thenReturn(new ProjectClosureCheckApi.GraphOverview(true, "SATISFIED", 10L, true));
        var reopened = new NormalClosureQueryService(contexts, graph, mapper, permissions,
                query -> java.util.Optional.of(new cn.iocoder.yudao.module.pms.project.api.projectgovernance.ProjectReopenScopeApi.Round(31L, opened)));
        var view = reopened.get(9L, new NormalClosureApplicationService.Actor(7L, 1L, "test"));
        assertNull(view.latestApplication());
        assertNull(view.latestSnapshot());
        assertEquals(List.of("CHECK"), view.allowedActions());
        assertEquals("APPROVED", application.getStatus());
        assertEquals(opened.minusDays(1), snapshot.getCheckedAt());
        application.setStatus("IN_REVIEW");
        var pending = reopened.get(9L, new NormalClosureApplicationService.Actor(7L, 1L, "test"));
        assertSame(application, pending.latestApplication());
        assertTrue(pending.allowedActions().isEmpty());
    }

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
