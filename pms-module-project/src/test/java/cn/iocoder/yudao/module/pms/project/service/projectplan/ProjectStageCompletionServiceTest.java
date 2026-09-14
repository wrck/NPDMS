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
    final cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi nodeContexts = mock(cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi.class);
    final cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService business = mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessService.class);
    final ProjectRuleCompiler compiler = new ProjectRuleCompiler();
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
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        var definition = new TemplateExecutionSnapshot.StageContract(); definition.setNodeKey(round.getNodeKey()); definition.setCode("DISCOVERY");
        var binding = new TemplateExecutionSnapshot.BindingContract(); binding.setType("STAGE_NATIVE"); definition.setBinding(binding);
        definition.setCompletionRuleKey("complete"); snapshot = new TemplateExecutionSnapshot(); snapshot.setStages(List.of(definition));
        snapshot.getRulePrograms().put("complete", compiler.compile(JsonUtils.parseTree("{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}")));
        plan = new ProjectPlanVersionDO(); plan.setId(21L); refreshSnapshot(); when(plans.selectEffective(any())).thenReturn(plan);
        service = new ProjectStageCompletionService(projects, plans, executions, graph, stages, references, engine.evaluator(), compiler,
                new ProjectRuntimeRuleEvaluator(new ProjectStageGateProviderRegistry(List.of(), mock(ProjectRuntimeGraphMapper.class), mock(ProjectNodeExecutionMapper.class)), compiler, engine.evaluator(), mock(ProjectDecisionTableService.class), mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService.class)), audit, nodeContexts, business);
    }
    void refreshSnapshot() { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); }

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
