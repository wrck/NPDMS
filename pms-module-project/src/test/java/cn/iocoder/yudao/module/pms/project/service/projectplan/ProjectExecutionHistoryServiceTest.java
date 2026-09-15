package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectExecutionHistoryServiceTest {
    final ProjectScopeApi scopes = mock(ProjectScopeApi.class);
    final PermissionApi permissions = mock(PermissionApi.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectExecutionHistoryService service = new ProjectExecutionHistoryService(scopes, permissions, plans, executions);
    final RuleEvaluation matched = new RuleEvaluation("plan:51:rule:complete:execution:31", RuleEvaluation.Outcome.MATCHED, null,
            List.of(new RuleEvaluation.Condition("leaf0", "$.children[0]", "pmsRuleFact", RuleEvaluation.Outcome.MATCHED, null)),
            List.of("pmsRuleFact"), List.of());

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L, 1L, Set.of(9L), Set.of()));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void everyRoundUsesItsOwnFrozenNamesAndResultsIncludingSupersededPlans() {
        var old = plan(51L, 1, "原需求确认", "SUPERSEDED");
        var current = plan(52L, 2, "改版需求确认", "EFFECTIVE");
        current.setClosureResult(JsonUtils.toJsonString(matched));
        var first = round(31L, 51L, 1, null); first.setResultSnapshot(JsonUtils.toJsonString(Map.of("completion", matched)));
        var second = round(32L, 52L, 2, 1);
        when(plans.selectHistory(any())).thenReturn(List.of(current, old));
        when(executions.selectHistory(any())).thenReturn(List.of(first, second));
        var result = service.get(9L, 1L);
        assertEquals("原需求确认", result.rounds().getFirst().name());
        assertEquals("改版需求确认", result.rounds().get(1).name());
        assertEquals(1, result.rounds().getFirst().planRevisionNo());
        assertEquals(2, result.rounds().get(1).roundNo());
        assertFalse(result.rounds().getFirst().current()); assertTrue(result.rounds().get(1).current());
        assertEquals("原需求确认完成", result.rounds().getFirst().evaluations().getFirst().name());
        assertEquals(matched, result.rounds().getFirst().evaluations().getFirst().result());
        assertEquals(matched, result.plans().getFirst().closure());
        verify(plans, never()).selectEffective(any());
        verify(executions).selectHistory(argThat(q -> q.tenantId().equals(7L) && q.projectId().equals(9L)));
    }

    @Test void viewingHistoryDoesNotDiscloseSubmissionTextWithoutHandlingPermission() {
        var round = round(31L,51L,1,1); round.setSubmissionNote("only-authorized-reader");
        when(plans.selectHistory(any())).thenReturn(List.of(plan(51L,1,"需求确认","EFFECTIVE")));
        when(executions.selectHistory(any())).thenReturn(List.of(round));
        var limited = service.get(9L,1L).rounds().getFirst();
        assertNull(limited.submissionNote()); assertFalse(limited.canViewSubmissionNote());
        when(permissions.hasAnyPermissions(1L,"pms:project-task:execute")).thenReturn(true);
        assertEquals("only-authorized-reader",service.get(9L,1L).rounds().getFirst().submissionNote());
    }

    @Test void businessCompletedStageReadsFrozenRulesWithoutInventingManualSubmission() {
        var frozen = plan(51L, 1, "阶段需求分析", "SUPERSEDED");
        var completed = round(31L, 51L, 1, null);
        completed.setSubmittedAt(null);
        completed.setSubmittedBy(null);
        completed.setResultSnapshot(JsonUtils.toJsonString(new StageCompletionEvidence(31L, 51L, matched, matched,
                List.of(new StageCompletionEvidence.BusinessResult(61L, "SOL", "REQUIREMENT_ANALYSIS", "71", "completed-v2")), null, null)));
        when(plans.selectHistory(any())).thenReturn(List.of(frozen));
        when(executions.selectHistory(any())).thenReturn(List.of(completed));

        var result = service.get(9L, 1L).rounds().getFirst();
        assertEquals("阶段需求分析", result.name());
        assertEquals("阶段需求分析完成", result.evaluations().getFirst().name());
        assertEquals(matched, result.evaluations().getFirst().result());
        assertNull(result.submittedAt());
        assertNull(result.submittedBy());
        assertNull(result.submissionNote());
        assertFalse(result.current());
        verify(plans, never()).selectEffective(any());
    }

    @Test void deniedProjectScopeReadsNoPlanOrExecutionHistory() {
        when(scopes.resolveCurrent(any())).thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        assertThrows(RuntimeException.class, () -> service.get(9L,1L));
        verifyNoInteractions(plans,executions,permissions);
    }

    @Test void globalHandlingPermissionWithoutProjectEditScopeDoesNotExposeNotes() {
        var round = round(31L,51L,1,1); round.setSubmissionNote("project-scoped-evidence");
        when(plans.selectHistory(any())).thenReturn(List.of(plan(51L,1,"需求确认","EFFECTIVE")));
        when(executions.selectHistory(any())).thenReturn(List.of(round));
        when(permissions.hasAnyPermissions(1L,"pms:project-task:execute")).thenReturn(true);
        when(scopes.resolveCurrent(argThat(q -> ProjectScopeApi.ACTION_EDIT.equals(q.actionCode()))))
                .thenReturn(new ProjectScopeResult(9L,1L,Set.of(),Set.of()));
        var result = service.get(9L,1L).rounds().getFirst();
        assertFalse(result.canViewSubmissionNote()); assertNull(result.submissionNote());
        assertEquals(round.getSubmittedAt(), result.submittedAt());
    }

    @Test void missingFrozenVersionNeverFallsBackToCurrentPlan() {
        when(plans.selectHistory(any())).thenReturn(List.of(plan(52L,2,"新名称","EFFECTIVE")));
        when(executions.selectHistory(any())).thenReturn(List.of(round(31L,51L,1,null)));
        var result = service.get(9L,1L).rounds().getFirst();
        assertEquals("stage:one",result.name()); assertNull(result.planRevisionNo()); assertTrue(result.evaluations().isEmpty());
    }

    @Test void taskGateResultUsesTheCompletedRoundsFrozenGateAndNeverTheReworkedPlan() {
        var old = taskPlan(51L, 1, "原工勘门禁", "SUPERSEDED");
        var current = taskPlan(52L, 2, "改版工勘门禁", "EFFECTIVE");
        var first = round(31L, 51L, 1, null); first.setNodeKind("TASK"); first.setNodeKey("task:analysis");
        var gate = new RuleEvaluation("plan:51:gate:8:version:4", RuleEvaluation.Outcome.MATCHED, null,
                matched.conditions(), matched.steps(), List.of());
        first.setResultSnapshot(JsonUtils.toJsonString(Map.of("completion", matched, "exit", matched,
                "gate", gate, "gateSnapshot", "SURVEY_READY:PASSED:4")));
        String immutable = first.getResultSnapshot();
        var second = round(32L, 52L, 2, 1); second.setNodeKind("TASK"); second.setNodeKey("task:analysis");
        second.setStatus("PENDING"); second.setSubmittedAt(null); second.setSubmittedBy(null);
        when(plans.selectHistory(any())).thenReturn(List.of(current, old));
        when(executions.selectHistory(any())).thenReturn(List.of(first, second));

        var result = service.get(9L, 1L);
        assertEquals(List.of("completion", "exit", "gate"), result.rounds().getFirst().evaluations().stream()
                .map(ProjectExecutionHistoryService.Evaluation::purpose).toList());
        var history = result.rounds().getFirst().evaluations().get(2);
        assertEquals("原工勘门禁", history.name());
        assertEquals(gate, history.result());
        assertTrue(result.rounds().get(1).evaluations().isEmpty());
        assertEquals(immutable, first.getResultSnapshot());
        verify(plans, never()).selectEffective(any());
    }

    @Test void missingHistoricalGateEvidenceIsNotRecomputedOrInvented() {
        var old = taskPlan(51L, 1, "原工勘门禁", "SUPERSEDED");
        var first = round(31L, 51L, 1, null); first.setNodeKind("TASK"); first.setNodeKey("task:analysis");
        first.setResultSnapshot(JsonUtils.toJsonString(Map.of("completion", matched)));
        when(plans.selectHistory(any())).thenReturn(List.of(old));
        when(executions.selectHistory(any())).thenReturn(List.of(first));
        assertEquals(List.of("completion"), service.get(9L, 1L).rounds().getFirst().evaluations().stream()
                .map(ProjectExecutionHistoryService.Evaluation::purpose).toList());
    }

    private ProjectPlanVersionDO taskPlan(Long id, int revision, String gateName, String status) {
        var plan = plan(id, revision, "工前准备", status);
        var snapshot = JsonUtils.parseObject(plan.getExecutionSnapshot(), TemplateExecutionSnapshot.class);
        var task = new TemplateExecutionSnapshot.TaskContract(); task.setNodeKey("task:analysis");
        task.setCode("ANALYSIS"); task.setName("需求分析"); task.setStageCode("DISCOVERY");
        task.setCompletionRuleKey("complete"); task.setExitRuleKey("complete"); task.setGateRef("SURVEY_READY");
        snapshot.setTasks(List.of(task));
        var gate = new TemplateExecutionSnapshot.GateContract(); gate.setNodeKey("gate:survey");
        gate.setCode("SURVEY_READY"); gate.setName(gateName); gate.setConditionRuleKey("survey-gate");
        snapshot.setGates(List.of(gate));
        plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        return plan;
    }

    private ProjectPlanVersionDO plan(Long id, int revision, String name, String status) {
        var snapshot = new TemplateExecutionSnapshot();
        var node = new TemplateExecutionSnapshot.StageContract(); node.setNodeKey("stage:one"); node.setCode("DISCOVERY");
        node.setName(name); node.setCompletionRuleKey("complete"); snapshot.setStages(List.of(node));
        snapshot.setRules(List.of(new VersionRule("complete",name+"完成",VersionRule.Kind.CONDITION,false,null,null)));
        var plan = new ProjectPlanVersionDO(); plan.setId(id); plan.setRevisionNo(revision); plan.setStatus(status);
        plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); return plan;
    }
    private ProjectNodeExecutionDO round(Long id, Long planId, int roundNo, Integer current) {
        var round = new ProjectNodeExecutionDO(); round.setId(id); round.setPlanVersionId(planId); round.setNodeKind("STAGE");
        round.setNodeKey("stage:one"); round.setRoundNo(roundNo); round.setCurrentMarker(current);
        round.setStatus("DONE"); round.setSubmittedAt(LocalDateTime.of(2026,9,14,1,0)); round.setSubmittedBy(1L);
        return round;
    }
}
