package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateFactProviderApi;
import cn.iocoder.yudao.module.pms.project.api.stagegate.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageGateRoundBoundaryTest {
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectStageGateFactProviderApi owner = mock(ProjectStageGateFactProviderApi.class);

    @ParameterizedTest @ValueSource(strings = {"BPM_APPROVAL", "BPM_PROCESS"})
    void replacesCallerBoundaryWithCurrentSourceStageRoundWithoutChangingAnotherStage(String key) {
        var registry = registry(key);
        var selected = round(31L, 11L, LocalDateTime.of(2026, 9, 15, 8, 0));
        var other = round(32L, 12L, selected.getCreateTime().plusDays(1));
        when(graph.selectStages(any())).thenReturn(List.of(stage(11L, "PREP"), stage(12L, "DELIVERY")));
        when(executions.selectCurrent(any())).thenReturn(List.of(selected, other));
        registry.lockAndRevalidate(key, query().forStageRound(java.time.Instant.EPOCH));
        verify(owner).lockAndRevalidate(argThat(q -> q.processStartedNotBefore()
                .equals(selected.getCreateTime().atZone(ZoneId.systemDefault()).toInstant())
                && q.gateReferenceId().equals(22L) && q.currentStageCode().equals("PREP")));
        verify(graph).selectStages(argThat(q -> q.tenantId().equals(7L) && q.projectId().equals(9L)));
        verify(executions).selectCurrent(argThat(q -> q.tenantId().equals(7L) && q.projectId().equals(9L)));
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "duplicate", "tenant", "project", "retired", "time", "task"})
    void missingOrUntrustedRoundCannotFallBackToAnOldApproval(String defect) {
        var registry = registry("BPM_APPROVAL");
        when(graph.selectStages(any())).thenReturn(List.of(stage(11L, "PREP")));
        var round = round(31L, 11L, LocalDateTime.of(2026, 9, 15, 8, 0));
        switch (defect) {
            case "tenant" -> round.setTenantId(8L);
            case "project" -> round.setProjectId(10L);
            case "retired" -> round.setCurrentMarker(null);
            case "time" -> round.setCreateTime(null);
            case "task" -> round.setNodeKind("TASK");
        }
        when(executions.selectCurrent(any())).thenReturn("missing".equals(defect) ? List.of()
                : "duplicate".equals(defect) ? List.of(round, round) : List.of(round));
        var result = registry.lockAndRevalidate("BPM_APPROVAL", query());
        assertEquals(ProjectStageGateOutcome.DEPENDENCY_UNAVAILABLE, result.outcome());
        assertEquals("APPROVAL_STAGE_ROUND_UNAVAILABLE", result.unmetCode());
        verify(owner, never()).lockAndRevalidate(any());
    }

    @Test void nonApprovalFactsDoNotReadOrRequireExecutionRounds() {
        var registry = registry("PROJ_TASK");
        assertEquals(ProjectStageGateOutcome.SATISFIED, registry.lockAndRevalidate("PROJ_TASK", query()).outcome());
        verifyNoInteractions(graph, executions);
    }

    private ProjectStageGateProviderRegistry registry(String key) {
        when(owner.providerKeys()).thenReturn(Set.of(key));
        when(owner.lockAndRevalidate(any())).thenReturn(new ProjectStageGateFact(key, "APPROVAL", "pi", "def", "fact",
                ProjectStageGateOutcome.SATISFIED, null));
        return new ProjectStageGateProviderRegistry(List.of(owner), graph, executions);
    }

    private ProjectStageGateFactQuery query() {
        return new ProjectStageGateFactQuery(7L, 9L, "PREP", 21L, "GATE", 0, 22L, 0, "APPROVAL", "approval", "def-1", null);
    }

    private ProjectStageInstanceDO stage(Long id, String code) {
        var stage = new ProjectStageInstanceDO();
        stage.setId(id); stage.setTenantId(7L); stage.setProjectId(9L); stage.setStageCode(code);
        return stage;
    }

    private ProjectNodeExecutionDO round(Long id, Long stageId, LocalDateTime createdAt) {
        var round = new ProjectNodeExecutionDO();
        round.setId(id); round.setTenantId(7L); round.setProjectId(9L); round.setNodeKind("STAGE");
        round.setNodeInstanceId(stageId); round.setCurrentMarker(1); round.setCreateTime(createdAt);
        return round;
    }
}
