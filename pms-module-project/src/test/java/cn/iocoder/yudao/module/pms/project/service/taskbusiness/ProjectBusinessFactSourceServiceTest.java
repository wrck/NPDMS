package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectStageExecutionContext;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessFactSourceServiceTest {
    private static RuleEngineTestFixture engine;
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }
    private final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    private final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    private final ProjectTaskExecutionContractMapper contracts = mock(ProjectTaskExecutionContractMapper.class);
    private final ProjectNodeExecutionApi contexts = mock(ProjectNodeExecutionApi.class);
    private final ProjectTaskBusinessService business = mock(ProjectTaskBusinessService.class);
    private final ProjectBusinessFactSourceService sources = new ProjectBusinessFactSourceService(plans, executions, contracts, contexts, business);
    private final ProjectRuleCompiler compiler = new ProjectRuleCompiler();
    private final ProjectMasterDO project = new ProjectMasterDO();
    private final ProjectNodeExecutionDO round = new ProjectNodeExecutionDO();
    private final ProjectPlanVersionDO plan = new ProjectPlanVersionDO();
    private final TemplateExecutionSnapshot snapshot = new TemplateExecutionSnapshot();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        project.setId(9L); project.setTenantId(7L); project.setActivePlanVersionId(51L);
        plan.setId(51L);
        var stage = new TemplateExecutionSnapshot.StageContract(); stage.setNodeKey("prep"); stage.setCode("PREP_WORK");
        var binding = new TemplateExecutionSnapshot.BindingContract(); binding.setType("BUSINESS_OBJECT");
        binding.setTargetContextCode("SOL"); binding.setTargetObjectType("SITE_SURVEY"); stage.setBinding(binding);
        snapshot.setStages(List.of(stage));
        when(plans.selectEffective(any())).thenAnswer(call -> { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); return plan; });
        round.setId(61L); round.setTenantId(7L); round.setProjectId(9L); round.setPlanVersionId(51L);
        round.setNodeKey("prep"); round.setNodeKind("STAGE"); round.setNodeInstanceId(21L); round.setContractId(31L);
        round.setRoundNo(1); round.setCurrentMarker(1); round.setStatus("DONE");
        freeze(Map.of("SURVEY_CONFIRMED", true));
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); TransactionSynchronizationManager.clear(); }

    @Test void completedSourceUsesItsFrozenResultEvenAfterPlanRevisionWithoutQueryingOwner() {
        assertTrue(evaluate(false).matched());
        project.setActivePlanVersionId(52L); plan.setId(52L);
        assertTrue(evaluate(false).matched());
        assertEquals(51L, round.getPlanVersionId());
        verifyNoInteractions(business, contexts, contracts);
    }
    @Test void pendingReworkNeverFallsBackToPreviousRoundOrPassesThroughNot() {
        String previous = round.getResultSnapshot();
        round.setId(62L); round.setRoundNo(2); round.setStatus("PENDING"); round.setResultSnapshot(null);
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
        assertNotNull(previous); verifyNoInteractions(business, contexts, contracts);
        round.setStatus("DONE"); round.setResultSnapshot(previous);
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
        freeze(Map.of("SURVEY_CONFIRMED", false));
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, evaluate(false).outcome());
        assertTrue(evaluate(true).matched());
    }
    @Test void missingFactEmptyEvidenceAndTerminatedSourceStayUnknown() {
        freeze(Map.of("OTHER", true));
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
        round.setResultSnapshot("{}");
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
        freeze(Map.of("SURVEY_CONFIRMED", true)); round.setStatus("TERMINATED");
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
    }
    @Test void activeStageReadsItsExactCurrentExecutionAndDoesNotRequireCompletionToExposeFalse() {
        round.setStatus("ACTIVE"); round.setResultSnapshot(null);
        var context = new ProjectStageExecutionContext(9L, 1, 21L, 1, 31L, 1, 51L, 61L, 1, 1, true);
        when(contexts.inspectStage(any())).thenReturn(context);
        when(business.lockStageCompletionFacts(eq(7L), eq(context), any())).thenReturn(new TaskBusinessCompletionFacts(
                new TaskBusinessLinkedFacts("result-v2", List.of(link(Map.of("SURVEY_CONFIRMED", false)))), false));
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, evaluate(false).outcome());
        verify(business).lockStageCompletionFacts(eq(7L), eq(context), any());
        verifyNoInteractions(contracts);
    }
    @Test void unknownOwnerResultIsNotNegatedIntoAdmission() {
        round.setStatus("ACTIVE");
        when(contexts.inspectStage(any())).thenReturn(new ProjectStageExecutionContext(9L, 1, 21L, 1, 31L, 1, 51L, 61L, 1, 1, true));
        when(business.lockStageCompletionFacts(any(), any(), any())).thenThrow(new IllegalStateException("unavailable"));
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
    }
    @Test void activeTaskUsesTheSourceTaskContractAndCompletedTaskUsesTheSameFrozenEvidence() {
        var task = new TemplateExecutionSnapshot.TaskContract(); task.setNodeKey("prep");
        task.setCode("SURVEY"); task.setStageCode("PREP_WORK");
        snapshot.setTasks(List.of(task)); snapshot.setStages(List.of()); round.setNodeKind("TASK");
        assertTrue(evaluate(false).matched());
        round.setStatus("ACTIVE"); round.setResultSnapshot(null);
        var context = new cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext(
                9L, 1, 21L, 1, 31L, 1, 51L, 61L, 1, 1, 81L, 1, true, java.time.LocalDateTime.now());
        var contract = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO();
        contract.setId(31L);
        when(contexts.inspect(any())).thenReturn(context); when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(business.lockCompletionFacts(7L, context, contract)).thenReturn(new TaskBusinessCompletionFacts(
                new TaskBusinessLinkedFacts("result-v2", List.of(link(Map.of("SURVEY_CONFIRMED", true)))), true));
        assertTrue(evaluate(false).matched());
        verify(business).lockCompletionFacts(7L, context, contract);
        contract.setId(32L);
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
        verify(business, times(1)).lockCompletionFacts(any(), any(), any());
    }
    @Test void wrongTenantPlanOrDuplicateCurrentSourceCannotProvideFacts() {
        round.setTenantId(8L); assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
        round.setTenantId(7L); plan.setId(52L); assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
        plan.setId(51L); when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round, round));
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, evaluate(true).outcome());
        verifyNoInteractions(business, contexts, contracts);
    }
    @Test void frozenEvidenceDoesNotShareMutableFactMaps() {
        var values = new java.util.HashMap<>(Map.of("SURVEY_CONFIRMED", true));
        var evidence = ProjectBusinessFactSourceService.freeze(round, List.of(link(values)));
        values.put("SURVEY_CONFIRMED", false);
        assertTrue(evidence.results().getFirst().facts().get("SURVEY_CONFIRMED"));
        assertThrows(UnsupportedOperationException.class, () -> evidence.results().getFirst().facts().clear());
    }
    private RuleEvaluation evaluate(boolean not) {
        String json = "{\"predicate\":\"BUSINESS_FACT\",\"parameters\":{\"sourceNodeKey\":\"prep\",\"factCode\":\"SURVEY_CONFIRMED\",\"quantifier\":\"ALL\"}}";
        if (not) json = "{\"operator\":\"NOT\",\"rules\":[" + json + "]}";
        var runtime = new ProjectRuntimeRuleEvaluator(new ProjectStageGateProviderRegistry(List.of()), compiler,
                engine.evaluator(), mock(ProjectDecisionTableService.class), sources);
        return runtime.evaluate("plan:" + plan.getId() + ":consumer", compiler.compile(JsonUtils.parseTree(json)),
                new ProjectRuntimeRuleEvaluator.Facts(project, null, List.of(), List.of(), List.of(), false));
    }
    private void freeze(Map<String, Boolean> values) {
        round.setResultSnapshot(JsonUtils.toJsonString(Map.of("businessFacts", ProjectBusinessFactSourceService.freeze(round, List.of(link(values))))));
    }
    private TaskBusinessLinkFact link(Map<String, Boolean> values) {
        return new TaskBusinessLinkFact(71L, "81", "工勘", "result-v1", values, List.of(), Set.of());
    }
}
