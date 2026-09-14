package cn.iocoder.yudao.module.pms.project.service.stagegate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectStageExecutionRecord;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectGateRuleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageGateWorkbenchServiceTest {
    final ProjectManualCreationService access = mock(ProjectManualCreationService.class);
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectGateReferenceInstanceMapper references = mock(ProjectGateReferenceInstanceMapper.class);
    final ProjectGateRuleService rules = mock(ProjectGateRuleService.class);
    final ProjectStageGateWorkbenchService service = new ProjectStageGateWorkbenchService(access, projects, graph, executions, references, rules);
    final ProjectAccessActor actor = new ProjectAccessActor(7L, 1L);
    ProjectMasterDO project;
    ProjectStageInstanceDO stage;
    ProjectNodeExecutionDO round;
    ProjectGateInstanceDO gate;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setVersion(4);
        project.setActivePlanVersionId(51L); project.setLifecycleStatus("ACTIVE"); project.setCurrentStage("ANOTHER_ACTIVE_STAGE");
        when(access.getProject(9L, actor)).thenReturn(project);
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        stage = new ProjectStageInstanceDO(); stage.setId(11L); stage.setStageCode("PREP"); stage.setTenantId(7L);
        stage.setProjectId(9L); stage.setStatus("ACTIVE");
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage));
        round = new ProjectNodeExecutionDO(); round.setId(61L); round.setTenantId(7L); round.setProjectId(9L);
        round.setPlanVersionId(51L); round.setNodeInstanceId(11L); round.setNodeKind("STAGE"); round.setCurrentMarker(1);
        round.setRoundNo(2); round.setContractId(71L);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        when(executions.selectCurrentStageContextForUpdate(any())).thenReturn(new ProjectStageExecutionRecord(
                9L, 4, "ACTIVE", 11L, 0, "ACTIVE", 71L, 1, 51L, 61L, 0, 2, "ACTIVE"));
        gate = gate(21L, "READY", "PREP");
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of(gate));
        var reference = new ProjectGateReferenceInstanceDO(); reference.setId(31L); reference.setTenantId(7L);
        reference.setGateId(21L); reference.setRefType("APPROVAL"); reference.setRefCode("review"); reference.setRefVersion("review:2:222");
        when(references.selectOrderedForUpdate(any())).thenReturn(List.of(reference));
        when(rules.inspect(9L, "READY")).thenReturn(outcome("READY", RuleEvaluation.Outcome.NOT_MATCHED));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void selectedParallelStageReturnsRealRoundAndReferencesWithoutCallingAdvanceOrMutation() {
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of(gate, gate(22L, "OTHER", "ANOTHER_ACTIVE_STAGE")));
        var result = service.inspect(9L, "PREP", actor);
        assertNull(result.recoverableError()); assertEquals(51L, result.planVersionId());
        assertEquals(61L, result.executionId()); assertEquals(2, result.executionRound()); assertEquals(4, result.projectVersion());
        assertEquals(1, result.gates().size());
        var actual = result.gates().getFirst();
        assertEquals(21L, actual.gateId()); assertEquals("PASSED", actual.persistedStatus());
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, actual.evaluation().outcome());
        assertEquals(new ProjectStageGateWorkbench.Reference(31L, "APPROVAL", "review", "review:2:222"), actual.references().getFirst());
        verify(rules).inspect(9L, "READY"); verifyNoMoreInteractions(rules);
        verify(references).selectOrderedForUpdate(argThat(query -> query.tenantId() == 7L && query.gateIds().equals(List.of(21L))));
    }

    @Test void unknownGateDoesNotHideAnIndependentGateAndDoesNotExposeOwnerValues() {
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of(gate, gate(22L, "INDEPENDENT", "PREP")));
        when(rules.inspect(9L, "READY")).thenReturn(outcome("READY", RuleEvaluation.Outcome.UNKNOWN));
        when(rules.inspect(9L, "INDEPENDENT")).thenReturn(outcome("INDEPENDENT", RuleEvaluation.Outcome.MATCHED));
        var result = service.inspect(9L, "PREP", actor);
        assertEquals(List.of(RuleEvaluation.Outcome.UNKNOWN, RuleEvaluation.Outcome.MATCHED),
                result.gates().stream().map(item -> item.evaluation().outcome()).toList());
        assertFalse(JsonUtils.toJsonString(result).contains("variables"));
        verify(rules, never()).evaluate(any(), any(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "duplicate", "old-plan", "old-round", "foreign", "wrong-contract"})
    void missingOrStaleRoundNeverReturnsAUsableGateContext(String failure) {
        switch (failure) {
            case "missing" -> when(executions.selectCurrentForUpdate(any())).thenReturn(List.of());
            case "duplicate" -> when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round, round));
            case "old-plan" -> round.setPlanVersionId(50L);
            case "old-round" -> round.setCurrentMarker(null);
            case "foreign" -> round.setTenantId(8L);
            case "wrong-contract" -> when(executions.selectCurrentStageContextForUpdate(any())).thenReturn(null);
        }
        var result = service.inspect(9L, "PREP", actor);
        assertEquals("STAGE_EXECUTION_UNAVAILABLE", result.recoverableError()); assertNull(result.executionId());
        assertTrue(result.gates().isEmpty()); verifyNoInteractions(references, rules);
    }

    @Test void projectReadScopeAndTrustedTenantAreRequiredBeforeReadingGateFacts() {
        when(access.getProject(9L, actor)).thenThrow(new IllegalArgumentException("denied"));
        assertThrows(IllegalArgumentException.class, () -> service.inspect(9L, "PREP", actor));
        assertThrows(RuntimeException.class, () -> service.inspect(9L, "PREP", new ProjectAccessActor(8L, 1L)));
        verifyNoInteractions(projects, graph, executions, references, rules);
    }

    @Test void absentStageAndEmptyGateSetDoNotBroadenTheReferenceQuery() {
        assertEquals("STAGE_NOT_FOUND", service.inspect(9L, "MISSING", actor).recoverableError());
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of());
        assertTrue(service.inspect(9L, "PREP", actor).gates().isEmpty());
        verifyNoInteractions(references, rules);
    }

    @Test void nullRuleResultIsUnknownButTransactionFailuresAreNotSwallowed() {
        when(rules.inspect(9L, "READY")).thenReturn(null);
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, service.inspect(9L, "PREP", actor).gates().getFirst().evaluation().outcome());
        when(rules.inspect(9L, "READY")).thenThrow(new IllegalStateException("transaction failed"));
        assertThrows(IllegalStateException.class, () -> service.inspect(9L, "PREP", actor));
    }

    private ProjectGateInstanceDO gate(Long id, String code, String stageCode) {
        var value = new ProjectGateInstanceDO(); value.setId(id); value.setTenantId(7L); value.setProjectId(9L);
        value.setStageCode(stageCode); value.setGateCode(code); value.setName(code); value.setGateType("ENTRY"); value.setStatus("PASSED");
        return value;
    }
    private RuleEvaluation outcome(String code, RuleEvaluation.Outcome outcome) {
        return new RuleEvaluation("plan:51:gate:" + code, outcome, outcome == RuleEvaluation.Outcome.UNKNOWN ? "FACT_UNAVAILABLE" : null,
                List.of(), List.of(), List.of());
    }
}
