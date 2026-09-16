package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectTaskPlanCompletionServiceTest {
    static RuleEngineTestFixture engine;
    @BeforeAll static void start() { engine=new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    final ProjectPlanVersionMapper plans=mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper executions=mock(ProjectNodeExecutionMapper.class);
    final ProjectRuntimeGraphMapper graph=mock(ProjectRuntimeGraphMapper.class);
    final ProjectGateReferenceInstanceMapper references=mock(ProjectGateReferenceInstanceMapper.class);
    final ProjectTaskBusinessService business=mock(ProjectTaskBusinessService.class);
    final ProjectTaskApprovalService approvals=mock(ProjectTaskApprovalService.class);
    final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi executionApi = mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi.class);
    final ProjectRuleCompiler compiler=new ProjectRuleCompiler();
    final cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectGateRuleService gateRules = mock(cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectGateRuleService.class);
    ProjectTaskPlanCompletionService service;
    ProjectMasterDO project;
    ProjectTaskInstanceDO task;
    ProjectStageInstanceDO stage;
    ProjectNodeExecutionDO round;
    ProjectTaskExecutionContractDO binding;
    ProjectPlanVersionDO plan;
    TemplateExecutionSnapshot snapshot;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        service=new ProjectTaskPlanCompletionService(plans,executions,graph,references,
                new ProjectRuntimeRuleEvaluator(new ProjectStageGateProviderRegistry(List.of(), mock(ProjectRuntimeGraphMapper.class), mock(ProjectNodeExecutionMapper.class)),compiler,engine.evaluator(),mock(ProjectDecisionTableService.class),mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService.class)),
                engine.evaluator(),compiler,business, executionApi,gateRules);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "approvals", approvals);
        project=new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setActivePlanVersionId(52L); project.setLifecycleStatus("ACTIVE"); project.setProjectName("private-actual-value");
        task=new ProjectTaskInstanceDO().setId(21L).setProjectId(9L).setCode("T1").setStageCode("A").setName("办理任务");
        stage=new ProjectStageInstanceDO().setId(11L).setProjectId(9L).setCode("A").setStatus("ACTIVE");
        binding=new ProjectTaskExecutionContractDO(); binding.setId(91L); binding.setContractVersion(1); binding.setProjectTaskId(21L);
        binding.setSourceNodeKey("task:one"); binding.setWorkBindingTypeCode("TASK_NATIVE"); binding.setCompletionRuleSnapshot("obsolete-binding-rule-must-not-be-read");
        round=new ProjectNodeExecutionDO(); round.setId(31L); round.setNodeInstanceId(21L); round.setNodeKind("TASK"); round.setNodeKey("task:one");
        round.setContractId(91L); round.setPlanVersionId(52L); round.setStatus("ACTIVE"); round.setSubmittedAt(LocalDateTime.now());
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage)); when(graph.selectTasksForUpdate(any())).thenReturn(List.of(task));
        plan=new ProjectPlanVersionDO(); plan.setId(52L);
        snapshot=new TemplateExecutionSnapshot(); var node=new TemplateExecutionSnapshot.TaskContract(); node.setNodeKey("task:one");node.setCode("T1");node.setStageCode("A");node.setCompletionRuleKey("done");
        snapshot.setTasks(List.of(node)); rule("done","{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}");
        when(plans.selectEffective(any())).thenAnswer(call -> { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); return plan; });
    }
    @Test void approvalCompletionRequiresThisRoundOwnerResultBeforeAnyTrueOrNotRule() {
        binding.setWorkBindingTypeCode("APPROVAL");
        round.setSubmittedAt(null); round.setStartedAt(LocalDateTime.of(2026,9,15,9,0));
        for (var outcome : cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.values()) {
            var fact = new cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Fact(
                    outcome, "TEST", "approval-1", "definition:1", outcome.name());
            when(approvals.inspect(7L,9L,21L,binding,31L,round.getStartedAt())).thenReturn(fact);
            rule("done","{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}");
            var result = service.evaluateAutomatically(project,task,binding);
            assertEquals(outcome == cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.SATISFIED, result.matched());
            if (result.matched()) assertEquals(fact, result.evidence().get("approval"));
            else assertFalse(result.completion().matched());
        }
        verifyNoInteractions(business, executionApi);
    }
    @Test void approvedTaskStillRequiresConfiguredCompletionExitAndGateRules() {
        binding.setWorkBindingTypeCode("APPROVAL");
        when(approvals.inspect(any(),any(),any(),any(),any(),any())).thenReturn(
                new cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Fact(
                        cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi.Outcome.SATISFIED,
                        "APPROVED","approval-1","definition:1",null));
        rule("done","{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}");
        assertFalse(service.evaluateAutomatically(project,task,binding).matched());
        rule("done","{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}");
        snapshot.getTasks().getFirst().setExitRuleKey("exit");
        rule("exit","{\"operator\":\"NOT\",\"rules\":[{\"predicate\":\"APPROVAL\",\"parameters\":{\"refCode\":\"missing\"}}]}");
        assertEquals(RuleEvaluation.Outcome.UNKNOWN,service.evaluateAutomatically(project,task,binding).exit().outcome());
        assertFalse(service.evaluateAutomatically(project,task,binding).matched());
    }
    void rule(String key,String expression) { snapshot.getRulePrograms().put(key,compiler.compile(JsonUtils.parseTree(expression))); }
    TaskActionCommand command() { return new TaskActionCommand(21L,1,"complete",null,91L,1,null,null,null,null,"a".repeat(64),"intent","b".repeat(64)); }
    ProjectTaskPlanCompletionService.Result evaluate() { return service.evaluate(command(),project,task,binding,new TaskWorkbenchActor(7L,1L,"test")); }

    @Test void readsPlanRulesWithoutChangingTheBindingIdentityOrSnapshot() {
        assertTrue(evaluate().matched());
        rule("done","{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}");
        assertFalse(evaluate().matched());
        assertEquals(91L,binding.getId()); assertEquals("obsolete-binding-rule-must-not-be-read",binding.getCompletionRuleSnapshot());
        assertTrue(evaluate().completion().ruleVersionRef().contains("plan:52")); verifyNoInteractions(business);
    }

    @Test void ownActivationWaitUsesTheTaskRoundNotItsParentAndStillRequiresRealSubmission() {
        var evaluator = (ProjectRuntimeRuleEvaluator) org.springframework.test.util.ReflectionTestUtils.getField(service, "facts");
        org.springframework.test.util.ReflectionTestUtils.setField(evaluator, "relativeTime",
                new cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRelativeTimeFacts(executions));
        when(executions.selectCurrent(any())).thenReturn(List.of(round));
        rule("done", "{\"predicate\":\"WAIT_ELAPSED\",\"parameters\":{\"anchor\":\"NODE_ACTIVATED\",\"duration\":\"PT1H\"}}");
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate().completion().outcome());
        round.setAdmittedAt(LocalDateTime.now()); assertFalse(evaluate().matched());
        round.setAdmittedAt(LocalDateTime.now().minusHours(2)); assertTrue(evaluate().matched());
        round.setSubmittedAt(null); assertFalse(service.evaluateAutomatically(project, task, binding).matched());
        verifyNoInteractions(business);
    }
    @Test void currentPlanGateIsReevaluatedInsteadOfUsingOldBindingGateOrCachedPass() {
        binding.setGateRef("obsolete-gate");
        snapshot.getTasks().getFirst().setGateRef("current-gate");
        for (var outcome : RuleEvaluation.Outcome.values()) {
            var gate = new RuleEvaluation("plan:52:gate:81", outcome, "GATE_TEST_RESULT", List.of(), List.of(), List.of());
            when(gateRules.evaluate(eq(9L),eq("current-gate"),isNull(),anyString())).thenReturn(
                    new cn.iocoder.yudao.module.pms.project.service.projectplan.ProjectGateRuleService.Result(gate,"current-gate:"+outcome+":2"));
            var result = evaluate();
            assertEquals(outcome == RuleEvaluation.Outcome.MATCHED, result.matched());
            assertTrue(result.completion().matched());
            assertEquals(gate, result.gate());
            assertEquals(gate, result.evidence().get("gate"));
            if (outcome != RuleEvaluation.Outcome.MATCHED) assertTrue(result.unmet().contains("GATE_TEST_RESULT"));
        }
        verify(gateRules,never()).evaluate(any(),eq("obsolete-gate"),any(),any());
        assertEquals("obsolete-gate",binding.getGateRef());
    }

    @Test void gateRemovedFromCurrentPlanDoesNotRemainRequiredByFrozenHistoricalBinding() {
        binding.setGateRef("obsolete-gate");
        assertTrue(evaluate().matched());
        verifyNoInteractions(gateRules);
    }
    @Test void constantTrueCannotReplaceThisRoundsActualManualSubmission() {
        round.setSubmittedAt(null); rule("done","{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}");
        assertEquals("CURRENT_ROUND_SUBMISSION_REQUIRED",evaluate().completion().reasonCode()); assertFalse(evaluate().matched());
    }
    @Test void automaticTimeCompletionStillRequiresCurrentRoundManualSubmission() {
        rule("done", "{\"predicate\":\"TIME_REACHED\",\"parameters\":{\"at\":\"2020-01-01T00:00:00Z\"}}");
        round.setSubmittedAt(null);
        assertEquals("CURRENT_ROUND_SUBMISSION_REQUIRED", service.evaluateAutomatically(project,task,binding).completion().reasonCode());
        round.setSubmittedAt(LocalDateTime.now());
        assertTrue(service.evaluateAutomatically(project,task,binding).matched());
        rule("done", "{\"predicate\":\"TIME_REACHED\",\"parameters\":{\"at\":\"2099-01-01T00:00:00Z\"}}");
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, service.evaluateAutomatically(project,task,binding).completion().outcome());
        verifyNoInteractions(business,executionApi);
    }
    @Test void nativeCompletionCanCombineTypedFieldsAndDoesNotExposeActualValuesInEvidence() {
        rule("done","{\"operator\":\"ALL\",\"rules\":[{\"predicate\":\"TASK_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}},{\"predicate\":\"FIELD\",\"parameters\":{\"fieldCode\":\"project.projectName\",\"valueType\":\"TEXT\",\"operator\":\"=\",\"value\":\"private-actual-value\"}}]}");
        var result=evaluate(); assertTrue(result.matched()); assertEquals(2,result.completion().conditions().size());
        assertFalse(JsonUtils.toJsonString(result.evidence()).contains("private-actual-value"));
    }
    @Test void unknownExitConditionCannotCompleteANativeTask() {
        snapshot.getTasks().getFirst().setExitRuleKey("exit");
        rule("exit","{\"operator\":\"NOT\",\"rules\":[{\"predicate\":\"APPROVAL\",\"parameters\":{\"refCode\":\"missing\"}}]}");
        assertTrue(evaluate().completion().matched()); assertEquals(RuleEvaluation.Outcome.UNKNOWN,evaluate().exit().outcome()); assertFalse(evaluate().matched());
    }
    @Test void oldRoundPlanAndInactiveStageCannotBeUsedToFinishWork() {
        round.setPlanVersionId(51L); assertEquals("TASK_EXECUTION_ROUND_STALE",evaluate().completion().reasonCode());
        round.setPlanVersionId(52L); stage.setStatus("DONE"); assertEquals("TASK_STAGE_NOT_ACTIVE",evaluate().completion().reasonCode());
    }
    @Test void missingOwnerFactUnderNotNeverFallsBackToNativeOrFalse() {
        binding.setWorkBindingTypeCode("BUSINESS_COMPONENT");
        when(business.lockAndRevalidateLinkedFacts(any(),any(),any(),any(),any())).thenReturn(new TaskBusinessLinkedFacts("a".repeat(64),
                List.of(new TaskBusinessLinkFact(71L,"owner:1","", "owner-v1",Map.of(),List.of(),Set.of()))));
        rule("done","{\"operator\":\"NOT\",\"rules\":[{\"predicate\":\"BUSINESS_FACT\",\"parameters\":{\"factCode\":\"OWNER_COMPLETED\",\"quantifier\":\"ALL\"}}]}");
        var result=evaluate(); assertEquals(RuleEvaluation.Outcome.UNKNOWN,result.completion().outcome()); assertFalse(result.matched());
    }
    @Test void realOwnerEvidenceCanBeCombinedWithTheCurrentPlanRules() {
        binding.setWorkBindingTypeCode("BUSINESS_COMPONENT");
        when(business.lockAndRevalidateLinkedFacts(any(),any(),any(),any(),any())).thenReturn(new TaskBusinessLinkedFacts("a".repeat(64),
                List.of(new TaskBusinessLinkFact(71L,"owner:1","", "owner-v2",Map.of("OWNER_COMPLETED",true),List.of(),Set.of()))));
        rule("done","{\"predicate\":\"BUSINESS_FACT\",\"parameters\":{\"factCode\":\"OWNER_COMPLETED\",\"quantifier\":\"ALL\"}}");
        assertTrue(evaluate().matched()); assertTrue(JsonUtils.toJsonString(evaluate().evidence()).contains("owner-v2"));
    }

    @Test void automaticCompletionRequiresRealHandlingEvenWithConstantTrue() {
        binding.setWorkBindingTypeCode("BUSINESS_COMPONENT");
        var context = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                9L, 1, 21L, 1, 91L, 1, 52L, 61L, 1, 1, 62L, 1, true, LocalDateTime.of(2026,9,14,9,0));
        when(executionApi.inspect(any())).thenReturn(context);
        var links = new TaskBusinessLinkedFacts("a".repeat(64), List.of(
                new TaskBusinessLinkFact(71L, "owner:1", "", "draft-v1", Map.of("OWNER_COMPLETED", false), List.of(), Set.of())));
        when(business.lockCompletionFacts(any(),any(),any())).thenReturn(new TaskBusinessCompletionFacts(links, false));
        rule("done","{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}");
        var result = service.evaluateAutomatically(project, task, binding);
        assertFalse(result.matched());
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, result.completion().outcome());
        assertEquals("BUSINESS_HANDLING_PENDING", result.completion().reasonCode());
        when(business.lockCompletionFacts(any(),any(),any())).thenReturn(new TaskBusinessCompletionFacts(links, true));
        assertTrue(service.evaluateAutomatically(project, task, binding).matched());
        verify(business, never()).lockAndRevalidateLinkedFacts(any(),any(),any(),any(),any());
    }
}
