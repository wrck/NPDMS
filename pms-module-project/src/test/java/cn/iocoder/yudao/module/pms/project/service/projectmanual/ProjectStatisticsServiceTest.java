package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.projection.ProjectStatistics;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectStatisticsServiceTest {
    private final ProjectTreeScopeService scope = mock(ProjectTreeScopeService.class);
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectStatisticsService service = new ProjectStatisticsService(scope, projects);

    @Test void emptyViewScopeReturnsZeroWithoutDatabaseQuery() {
        when(scope.resolveAllFullProjectIds(7L, 11L, "PROJECT_VIEW")).thenReturn(Set.of());
        assertEquals(new ProjectStatistics(), service.getStatistics(new ProjectAccessActor(7L, 11L)));
        verifyNoInteractions(projects);
    }

    @Test void passesOnlyServerResolvedTenantAndViewScopeToAggregate() {
        var expected = new ProjectStatistics(); expected.setTotal(2);
        when(scope.resolveAllFullProjectIds(7L, 11L, "PROJECT_VIEW")).thenReturn(Set.of(21L, 22L));
        when(projects.selectStatistics(any())).thenReturn(expected);
        assertSame(expected, service.getStatistics(new ProjectAccessActor(7L, 11L)));
        verify(scope).resolveAllFullProjectIds(7L, 11L, "PROJECT_VIEW");
        verify(projects).selectStatistics(argThat(q -> q.tenantId().equals(7L)
                && q.visibleProjectIds().equals(Set.of(21L, 22L)) && q.effectiveAt() != null));
        verifyNoMoreInteractions(scope, projects);
    }

    @Test void scopeFailureDoesNotFallBackToTenantWideStatistics() {
        when(scope.resolveAllFullProjectIds(7L, 11L, "PROJECT_VIEW")).thenThrow(new IllegalArgumentException("denied"));
        assertThrows(IllegalArgumentException.class, () -> service.getStatistics(new ProjectAccessActor(7L, 11L)));
        verifyNoInteractions(projects);
    }
}
