package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectRelativeTimeFactsTest {
    @Test void activationUsesAdmissionNotBusinessStartAndNeverFallsBackToOldRound() {
        var params = JsonUtils.parseTree("{\"anchor\":\"NODE_ACTIVATED\",\"duration\":\"PT30M\"}");
        var round = source(); round.setStatus("ACTIVE"); round.setAdmittedAt(LocalDateTime.now().minusHours(2));
        round.setStartedAt(LocalDateTime.now());
        assertEquals(round.getAdmittedAt().atZone(ZoneId.systemDefault()).toInstant().plusSeconds(1800),
                ProjectRelativeTimeFacts.boundary(params, 61L, List.of(round)).dueAt());
        assertNull(ProjectRelativeTimeFacts.boundary(params, 62L, List.of(round)));
        round.setAdmittedAt(null);
        assertNull(ProjectRelativeTimeFacts.boundary(params, 61L, List.of(round)));
    }

    @Test void explicitSourceReadsOnlyCurrentCompletedRoundIncludingPreservedOlderPlanHistory() {
        var params = JsonUtils.parseTree("{\"anchor\":\"NODE_COMPLETED\",\"duration\":\"PT30M\",\"sourceNodeKey\":\"task:survey\"}");
        var round = source(); round.setStatus("DONE"); round.setEndedAt(LocalDateTime.now().minusHours(1)); round.setPlanVersionId(2L);
        var mapper = mock(ProjectNodeExecutionMapper.class);
        when(mapper.selectCurrent(any())).thenReturn(List.of(round));
        var facts = new ProjectRelativeTimeFacts(mapper);
        assertEquals(true, facts.resolve(7L, 9L, 100L, params).value());
        verify(mapper).selectCurrent(argThat(scope -> scope.tenantId().equals(7L) && scope.projectId().equals(9L)));
        round.setId(62L); round.setStatus("ACTIVE"); round.setEndedAt(null);
        assertFalse(facts.resolve(7L, 9L, 100L, params).available());
        round.setStatus("TERMINATED"); round.setEndedAt(LocalDateTime.now().minusHours(1));
        assertFalse(facts.resolve(7L, 9L, 100L, params).available());
        round.setStatus("DONE"); round.setEndedAt(LocalDateTime.now());
        assertEquals(false, facts.resolve(7L, 9L, 100L, params).value());
        assertEquals(62L, ProjectRelativeTimeFacts.boundary(params, 100L, List.of(round)).executionId());
    }

    private ProjectNodeExecutionDO source() {
        var round = new ProjectNodeExecutionDO(); round.setId(61L); round.setNodeKey("task:survey"); round.setNodeKind("TASK");
        round.setTenantId(7L); round.setProjectId(9L); round.setCurrentMarker(1); return round;
    }
}
