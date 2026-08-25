package cn.iocoder.yudao.module.pms.project.service.projectprogress;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.service.projectprogress.command.ProjectProgressResult;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectProgressQueryServiceTest {

    @Test
    void filtersProgressDetailsWithExplicitViewScope() {
        ProjectProgressSnapshotService snapshotService = mock(ProjectProgressSnapshotService.class);
        ProjectTreeScopeService scopeService = mock(ProjectTreeScopeService.class);
        ProjectProgressQueryService service = new ProjectProgressQueryService(snapshotService, scopeService);
        ProjectProgressPolicyService.Actor actor = new ProjectProgressPolicyService.Actor(1L, 9L, "corr-1");
        ProjectProgressResult.Item visible = item(11L, "10.0000");
        ProjectProgressResult.Item hidden = item(12L, "90.0000");
        when(snapshotService.calculate(10L, actor)).thenReturn(new ProjectProgressResult(
                10L, 20L, 7L, "watermark", "READY", BigDecimal.TEN, List.of(visible, hidden)));
        ProjectScopeQuery query = new ProjectScopeQuery(1L, 9L, 10L, "PROJECT_VIEW", 7L);
        when(scopeService.resolve(query)).thenReturn(new ProjectTreeScopeService.ProjectTreeScope(
                10L, 7L, Set.of(10L, 11L), Set.of(), Set.of()));

        ProjectProgressResult result = service.getCurrent(10L, actor);

        assertEquals(List.of(visible), result.items());
        assertEquals(new BigDecimal("10.0000"), result.progress());
        verify(scopeService).resolve(query);
    }

    private ProjectProgressResult.Item item(Long projectId, String progress) {
        return new ProjectProgressResult.Item(
                projectId, 1L, new BigDecimal(progress), new BigDecimal("50.0000"),
                new BigDecimal(progress).multiply(new BigDecimal("0.5000")), null);
    }
}
