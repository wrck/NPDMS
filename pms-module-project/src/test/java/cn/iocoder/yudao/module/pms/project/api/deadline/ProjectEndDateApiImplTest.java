package cn.iocoder.yudao.module.pms.project.api.deadline;

import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProjectEndDateApiImplTest {
    @Test void planningMustUseSurveyDeadlineAndNeverWritesItBack() {
        var scope = mock(ProjectScopeApi.class); var mapper = mock(ProjectMasterMapper.class);
        var allowed = new ProjectScopeResult(7L, 1L, Set.of(7L), Set.of());
        when(scope.resolveCurrent(any())).thenReturn(allowed); when(scope.lockAndRevalidate(any())).thenReturn(allowed);
        var row = new ProjectMasterDO(); row.setId(7L); row.setTenantId(1L); row.setVersion(2); row.setLifecycleStatus("ACTIVE");
        row.setProjectEndDate(LocalDate.of(2026,12,31));
        when(mapper.selectEndDateForUpdate(any())).thenReturn(row);
        var api = new ProjectEndDateApiImpl(scope, mapper);
        api.validatePlanningEndDate(new ProjectEndDateCommand(1L,3L,7L,2,LocalDate.of(2026,12,31)));
        assertThrows(RuntimeException.class, () -> api.validatePlanningEndDate(new ProjectEndDateCommand(1L,3L,7L,2,LocalDate.of(2027,1,1))));
        verify(mapper, never()).updateEndDateIfMatch(any());
    }
    @Test void writesSurveyDeadlineToProjectWithoutAnyPlanningDependency() {
        var scope = mock(ProjectScopeApi.class); var mapper = mock(ProjectMasterMapper.class);
        var allowed = new ProjectScopeResult(7L, 1L, Set.of(7L), Set.of());
        when(scope.resolveCurrent(any())).thenReturn(allowed); when(scope.lockAndRevalidate(any())).thenReturn(allowed);
        var row = new ProjectMasterDO(); row.setId(7L); row.setTenantId(1L); row.setVersion(2); row.setLifecycleStatus("ACTIVE");
        when(mapper.selectEndDateForUpdate(any())).thenReturn(row); when(mapper.updateEndDateIfMatch(any())).thenReturn(1);
        var api = new ProjectEndDateApiImpl(scope, mapper);
        api.updateFromSurvey(new ProjectEndDateCommand(1L, 3L, 7L, 2, LocalDate.of(2026,12,31)));
        verify(mapper).updateEndDateIfMatch(argThat(q -> q.endDate().equals(LocalDate.of(2026,12,31)) && q.expectedVersion()==2 && q.tenantId()==1L));
        clearInvocations(mapper);
        assertThrows(RuntimeException.class, () -> api.updateFromSurvey(new ProjectEndDateCommand(1L,3L,7L,1,LocalDate.of(2026,12,30))));
        verify(mapper, never()).updateEndDateIfMatch(any());
        row.setLifecycleStatus("NORMAL_CLOSED");
        assertThrows(RuntimeException.class, () -> api.updateFromSurvey(new ProjectEndDateCommand(1L,3L,7L,2,LocalDate.of(2026,12,31))));
    }
    @Test void emptyScopeCannotChangeProjectDate() {
        var scope = mock(ProjectScopeApi.class); var mapper = mock(ProjectMasterMapper.class);
        when(scope.resolveCurrent(any())).thenReturn(new ProjectScopeResult(7L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class, () -> new ProjectEndDateApiImpl(scope,mapper).updateFromSurvey(new ProjectEndDateCommand(1L,3L,7L,2,LocalDate.now())));
        verifyNoInteractions(mapper);
    }
}
