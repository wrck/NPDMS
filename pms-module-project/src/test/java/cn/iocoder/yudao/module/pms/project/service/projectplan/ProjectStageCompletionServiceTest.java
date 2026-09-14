package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import cn.iocoder.yudao.module.pms.project.service.stagebusiness.ProjectStageApprovalService;
import cn.iocoder.yudao.module.pms.project.api.approval.ProjectNodeApprovalApi;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageCompletionServiceTest {
    static RuleEngineTestFixture engine;
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final ProjectStageInstanceMapper stages = mock(ProjectStageInstanceMapper.class);
    final ProjectGateReferenceInstanceMapper references = mock(ProjectGateReferenceInstanceMapper.class);
    final OperationAuditApi audit = mock(OperationAuditApi.class);
    final ProjectNodeApprovalApi approvals = mock(ProjectNodeApprovalApi.class);
    final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi nodeContexts = mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi.class);
    final cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService business = mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService.class);
    final ProjectRuleCompiler compiler = new ProjectRuleCompiler();
    final cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi processes = mock(cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi.class);
    ProjectStageCompletionService service;
    ProjectStageInstanceDO stage;
    ProjectNodeExecutionDO round;
    ProjectPlanVersionDO plan;
    TemplateExecutionSnapshot snapshot;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        var project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(21L);
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        stage = new ProjectStageInstanceDO().setId(11L).setProjectId(9L).setStageCode("DISCOVERY").setStatus("ACTIVE").setVersion(1);
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(stage));
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of()); when(graph.selectGatesForUpdate(any())).thenReturn(List.of());
        round = new ProjectNodeExecutionDO(); round.setId(31L); round.setPlanVersionId(21L); round.setNodeKey("stage:discovery");
        round.setNodeInstanceId(11L); round.setNodeKind("STAGE"); round.setStatus("ACTIVE"); round.setRoundNo(1); round.setVersion(1);
        round.setProjectId(9L); round.setTenantId(7L); round.setCurrentMarker(1);
        when(executions.selectById(31L)).thenReturn(round);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        var definition = new TemplateExecutionSnapshot.StageContract(); definition.setNodeKey(round.getNodeKey()); definition.setCode("DISCOVERY");
        var binding = new TemplateExecutionSnapshot.BindingContract(); binding.setType("STAGE_NATIVE"); definition.setBinding(binding);
        definition.setCompletionRuleKey("complete"); snapshot = new TemplateExecutionSnapshot(); snapshot.setStages(List.of(definition));
        snapshot.getRulePrograms().put("complete", compiler.compile(JsonUtils.parseTree("{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}")));
        plan = new ProjectPlanVersionDO(); plan.setId(21L); refreshSnapshot(); when(plans.selectEffective(any())).thenReturn(plan);
        service = new ProjectStageCompletionService(projects, plans, executions, graph, stages, references, engine.evaluator(), compiler,
                new ProjectRuntimeRuleEvaluator(new ProjectStageGateProviderRegistry(List.of(), mock(ProjectRuntimeGraphMapper.class), mock(ProjectNodeExecutionMapper.class)), compiler, engine.evaluator(), mock(ProjectDecisionTableService.class), mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService.class)), audit, nodeContexts, business, processes,
                new ProjectStageApprovalService(executions, approvals, nodeContexts));
    }
    void refreshSnapshot() { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); }

    private void approvalBinding() {
        var binding = snapshot.getStages().getFirst().getBinding();
        binding.setType("APPROVAL"); binding.setApprovalDefinitionKey("review");
        binding.setParameters(JsonUtils.parseTree("{\"processDefinitionId\":\"review:1\"}"));
        round.setContractId(41L); round.setStartedAt(LocalDateTime.of(2026,9,15,9,0));
        snapshot.getRulePrograms().put("complete", compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}")));
        refreshSnapshot();
        when(nodeContexts.inspectStage(any())).thenReturn(new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext(
                9L,1,11L,1,41L,1,21L,31L,1,1,true));
    }

    @Test void directApprovalRequiresActualCurrentRoundResultBeforeConstantRules() {
        approvalBinding();
        for (String status : List.of("NOT_STARTED", "RUNNING", "REJECTED", "CANCELLED")) {
            when(approvals.inspect(any())).thenReturn(new ProjectNodeApprovalApi.Fact(
                    ProjectNodeApprovalApi.Outcome.NOT_SATISFIED, status, "pi", "review:1", null));
            var result = service.completeStage(9L,11L,1L,"approval");
            assertEquals(0, result.completed()); assertFalse(result.unknown());
        }
        when(approvals.inspect(any())).thenReturn(ProjectNodeApprovalApi.Fact.unknown("missing evidence"));
        assertTrue(service.completeStage(9L,11L,1L,"unknown").unknown());
        when(approvals.inspect(any())).thenThrow(new IllegalStateException("owner unavailable"));
        assertTrue(service.completeStage(9L,11L,1L,"unavailable").unknown());
        verifyNoInteractions(business, stages, audit);
    }

    @Test void approvedStageStillRequiresCompletionExitAndFinishedChildrenThenFreezesApprovalEvidence() {
        approvalBinding();
        var approved = new ProjectNodeApprovalApi.Fact(ProjectNodeApprovalApi.Outcome.SATISFIED,"APPROVED","pi","review:1",null);
        when(approvals.inspect(any())).thenReturn(approved);
        snapshot.getStages().getFirst().setExitRuleKey("exit");
        snapshot.getRulePrograms().put("exit", compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}")));
        refreshSnapshot();
        assertEquals(0, service.completeStage(9L,11L,1L,"exit").completed());
        snapshot.getStages().getFirst().setExitRuleKey(null); refreshSnapshot();
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(new ProjectTaskInstanceDO().setStageCode("DISCOVERY").setStatus("IN_PROGRESS")));
        assertEquals(0, service.completeStage(9L,11L,1L,"children").completed());
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of());
        when(stages.updateStatusIfMatch(any())).thenReturn(1); when(executions.finishIfActive(any())).thenReturn(1);
        assertEquals(1,service.completeStage(9L,11L,1L,"completed").completed());
        verify(executions).finishIfActive(argThat(update -> {
            var evidence = JsonUtils.parseObject(update.resultSnapshot(), cn.iocoder.yudao.module.pms.project.domain.rule.StageCompletionEvidence.class);
            return approved.equals(evidence.approval()) && evidence.executionId().equals(31L)
                    && evidence.planVersionId().equals(21L) && evidence.businessResults().isEmpty();
        }));
        assertNull(round.getSubmittedAt()); verifyNoInteractions(business);
    }

    @Test void approvalWithoutStartedRoundDoesNotReadAnOldApprovedProcess() {
        approvalBinding(); round.setStartedAt(null);
        assertEquals(0,service.completeStage(9L,11L,1L,"not-started").completed());
        verifyNoInteractions(approvals, business, stages, audit);
    }

    @Test void approvedStageCannotBypassFalseCompletionOrAnUnknownConditionUnderNot() {
        approvalBinding();
        when(approvals.inspect(any())).thenReturn(new ProjectNodeApprovalApi.Fact(
                ProjectNodeApprovalApi.Outcome.SATISFIED,"APPROVED","pi","review:1",null));
        snapshot.getRulePrograms().put("complete",compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}")));
        refreshSnapshot();
        var rejected = service.completeStage(9L,11L,1L,"false-completion");
        assertEquals(0,rejected.completed()); assertFalse(rejected.unknown());
        snapshot.getRulePrograms().put("complete",compiler.compile(JsonUtils.parseTree("{\"operator\":\"NOT\",\"rules\":[{\"predicate\":\"BUSINESS_FACT\",\"parameters\":{\"factCode\":\"OWNER_COMPLETED\",\"quantifier\":\"ALL\"}}]}")));
        refreshSnapshot();
        var unknown = service.completeStage(9L,11L,1L,"unknown-condition");
        assertEquals(0,unknown.completed()); assertTrue(unknown.unknown());
        verifyNoInteractions(business,stages,audit);
    }

    @Test void runningGateWorkMustEndBeforeAStageCanCompleteEvenWithSatisfiedRules() {
        round.setSubmittedAt(LocalDateTime.now());
        var gate=new ProjectGateInstanceDO().setId(51L).setStageCode("DISCOVERY");
        var ref=new ProjectGateReferenceInstanceDO().setId(52L).setGateId(51L).setRefType("APPROVAL");
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of(gate)); when(references.selectOrderedForUpdate(any())).thenReturn(List.of(ref));
        when(processes.inspectRunning(any())).thenReturn(List.of(new cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcess("pi",52L,"DISCOVERY")));
        var blocked=service.completeStage(9L,11L,1L,"running");
        assertEquals(0,blocked.completed()); assertFalse(blocked.unknown()); verifyNoInteractions(stages,audit);
        when(processes.inspectRunning(any())).thenThrow(new IllegalStateException("unavailable"));
        assertTrue(service.completeStage(9L,11L,1L,"unknown").unknown()); verifyNoInteractions(stages,audit);
        doReturn(List.of(new cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcess("other",99L,"OTHER"))).when(processes).inspectRunning(any());
        when(stages.updateStatusIfMatch(any())).thenReturn(1); when(executions.finishIfActive(any())).thenReturn(1);
        assertEquals(1,service.completeStage(9L,11L,1L,"ended").completed());
    }

    @Test void evenConstantTrueCannotReplaceTheCurrentRoundsRealSubmission() {
        snapshot.getRulePrograms().put("complete", compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}")));
        refreshSnapshot();
        assertEquals(0, service.completeStage(9L, 11L, 1L, "test").completed()); verifyNoInteractions(stages, audit);
    }
    @Test void submittedNativeWorkCompletesThroughVersionedCommandsAndKeepsEvidence() {
        round.setSubmittedAt(LocalDateTime.now());
        when(stages.updateStatusIfMatch(any())).thenReturn(1); when(executions.finishIfActive(any())).thenReturn(1);
        assertEquals(1, service.completeStage(9L, 11L, 1L, "test").completed());
        verify(executions).finishIfActive(argThat(update -> update.executionId().equals(31L) && update.resultSnapshot().contains("pmsRulePredicate")));
        verify(audit).record(eq(7L), eq(1L), eq("test"), eq("PROJECT_STAGE_COMPLETED"), eq("PROJECT_STAGE"), eq("11"), eq("SUCCESS"), anyMap());
    }
    @Test void startedWorkCannotBeSilentlyDiscardedEvenAfterStageSubmission() {
        round.setSubmittedAt(LocalDateTime.now());
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(new ProjectTaskInstanceDO().setStageCode("DISCOVERY").setStatus("IN_PROGRESS")));
        assertEquals(0, service.completeStage(9L, 11L, 1L, "test").completed()); verifyNoInteractions(stages, audit);
    }
    @Test void anUnstartedBranchIsRequiredOnlyByTheConfiguredRules() {
        round.setSubmittedAt(LocalDateTime.now());
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(new ProjectTaskInstanceDO().setStageCode("DISCOVERY").setStatus("PENDING_ASSIGN")));
        when(stages.updateStatusIfMatch(any())).thenReturn(1); when(executions.finishIfActive(any())).thenReturn(1);
        assertEquals(1, service.completeStage(9L, 11L, 1L, "test").completed());
    }
    @Test void missingExitProgramAndMissingRoundNeverFallBackToTrue() {
        round.setSubmittedAt(LocalDateTime.now()); snapshot.getStages().getFirst().setExitRuleKey("missing"); refreshSnapshot();
        assertTrue(service.completeStage(9L, 11L, 1L, "test").unknown()); verifyNoInteractions(stages, audit);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of());
        assertTrue(service.completeStage(9L, 11L, 1L, "test").unknown());
    }

    @Test void completedOwnerResultEndsStageWithoutManualSubmissionAndFreezesAssociationEvidence() {
        businessResult(true,java.util.Map.of("OWNER_COMPLETED",true));
        when(stages.updateStatusIfMatch(any())).thenReturn(1); when(executions.finishIfActive(any())).thenReturn(1);
        assertNull(round.getSubmittedAt());
        assertEquals(1,service.completeStage(9L,11L,1L,"test").completed());
        verify(executions).finishIfActive(argThat(update -> update.resultSnapshot().contains("owner-result-2")
                && update.resultSnapshot().contains("businessResults") && update.resultSnapshot().contains("pmsRulePredicate")));
        verify(executions).finishIfActive(argThat(update -> JsonUtils.parseTree(update.resultSnapshot())
                .path("businessFacts").path("results").path(0).path("facts").path("OWNER_COMPLETED").asBoolean()));
        assertNull(round.getSubmittedAt());
    }

    @Test void constantTrueCannotReplaceActualOwnerHandling() {
        businessResult(false,java.util.Map.of("OWNER_COMPLETED",false));
        snapshot.getRulePrograms().put("complete",compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}")));
        refreshSnapshot();
        var result = service.completeStage(9L,11L,1L,"test");
        assertEquals(0,result.completed()); assertFalse(result.unknown()); verifyNoInteractions(stages,audit);
    }

    @Test void unknownOwnerConditionUnderNotNeverEndsStage() {
        businessResult(true,java.util.Map.of());
        snapshot.getRulePrograms().put("complete",compiler.compile(JsonUtils.parseTree("{\"operator\":\"NOT\",\"rules\":[{\"predicate\":\"BUSINESS_FACT\",\"parameters\":{\"factCode\":\"OWNER_COMPLETED\",\"quantifier\":\"ALL\"}}]}")));
        refreshSnapshot();
        var result = service.completeStage(9L,11L,1L,"test");
        assertEquals(0,result.completed()); assertTrue(result.unknown()); verifyNoInteractions(stages,audit);
    }

    @Test void realBusinessCompletionDoesNotDiscardStartedChildren() {
        businessResult(true,java.util.Map.of("OWNER_COMPLETED",true));
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(new ProjectTaskInstanceDO().setStageCode("DISCOVERY").setStatus("IN_PROGRESS")));
        assertEquals(0,service.completeStage(9L,11L,1L,"test").completed()); verifyNoInteractions(stages,audit);
    }

    private void businessResult(boolean completed,java.util.Map<String,Boolean> facts) {
        var node = snapshot.getStages().getFirst();
        node.getBinding().setType("BUSINESS_OBJECT"); node.getBinding().setTargetContextCode("SOL"); node.getBinding().setTargetObjectType("REQUIREMENT_ANALYSIS");
        snapshot.getRulePrograms().put("complete",compiler.compile(JsonUtils.parseTree("{\"predicate\":\"BUSINESS_FACT\",\"parameters\":{\"factCode\":\"OWNER_COMPLETED\",\"quantifier\":\"ALL\"}}")));
        round.setContractId(41L); refreshSnapshot();
        var execution = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext(9L,1,11L,1,41L,1,21L,31L,1,1,true);
        when(nodeContexts.inspectStage(any())).thenReturn(execution);
        var link = new cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessLinkFact(51L,"61","业务完成事实","owner-result-2",facts,List.of(),java.util.Set.of());
        when(business.lockStageCompletionFacts(eq(7L),eq(execution),any())).thenReturn(
                new cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessCompletionFacts(
                        new cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessLinkedFacts("v2",List.of(link)),completed));
    }
}
