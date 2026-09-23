package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.domain.deliveryconfiguration.StageTransitionTargetResolver;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ProjectRuntimeGraphResolverTest {
    static cn.iocoder.yudao.module.pms.project.service.rule.RuleEngineTestFixture engine;
    @BeforeAll static void startRules() { engine = new cn.iocoder.yudao.module.pms.project.service.rule.RuleEngineTestFixture(); }
    @AfterAll static void stopRules() { engine.close(); }
    ProjectRuntimeGraphMapper mapper;
    ProjectRuntimeGraphResolver resolver;
    ProjectMasterDO project;
    List<ProjectStageInstanceDO> stages;
    static final String SNAPSHOT = "[{\"definition\":{\"id\":100,\"definitionKind\":\"COMPLETION_RULE\",\"schemaVersion\":1,\"payload\":{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}}},{\"definition\":{\"id\":101,\"definitionKind\":\"COMPLETION_RULE\",\"schemaVersion\":1,\"payload\":{\"predicate\":\"PROCESS\",\"parameters\":{\"refCode\":\"unknown\"}}}}]";

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        mapper = mock(ProjectRuntimeGraphMapper.class);
        resolver = new ProjectRuntimeGraphResolver(mapper, mock(ProjectGateReferenceInstanceMapper.class),
                new ProjectRuntimeRuleEvaluator(new ProjectStageGateProviderRegistry(List.of(), mock(ProjectRuntimeGraphMapper.class), mock(ProjectNodeExecutionMapper.class)),
                        new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler(), engine.evaluator(),
                        mock(cn.iocoder.yudao.module.pms.project.service.rule.ProjectDecisionTableService.class),
                        mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService.class), new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields(key -> null)));
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L);
        project.setLifecycleStatus("ACTIVE"); project.setCurrentStage("S0");
        stages = List.of(stage(1L, "S0", 99, true, false), stage(2L, "S4", 0, false, false), stage(3L, "S6", 1, false, true));
        when(mapper.selectStagesForUpdate(any())).thenReturn(stages);
        when(mapper.selectContracts(any())).thenReturn(stages.stream().map(s -> {
            var c = new ProjectStageExecutionContractDO(); c.setTenantId(7L); c.setProjectId(9L); c.setStageId(s.getId());
            c.setGraphVersion(1L); c.setDefinitionRevisionId(s.getDefinitionRevisionId()); c.setCompletionRuleRevisionId(100L);
            c.setDefinitionSnapshot(SNAPSHOT); return c;
        }).toList());
        when(mapper.selectTransitions(any())).thenReturn(List.of(edge(1L, 2L), edge(2L, 3L)));
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    @Test void followsS0ToS4AndS4ToS6IndependentOfSort() {
        assertEquals("S4", resolver.resolve(project).target().getCode());
        stages.getFirst().setStatus("DONE"); stages.get(1).setStatus("ACTIVE"); project.setCurrentStage("S4");
        assertEquals("S6", resolver.resolve(project).target().getCode());
    }
    @Test void closureIncludesEarlierExitGatesButNotUnvisitedBranchesOrEntryGates() {
        stages.getFirst().setStatus("DONE"); stages.get(1).setStatus("SKIPPED"); stages.getLast().setStatus("ACTIVE");
        var oldExit = new ProjectGateInstanceDO(); oldExit.setId(11L); oldExit.setStageCode("S0"); oldExit.setGateType("EXIT");
        var skippedExit = new ProjectGateInstanceDO(); skippedExit.setId(12L); skippedExit.setStageCode("S4"); skippedExit.setGateType("EXIT");
        var finalExit = new ProjectGateInstanceDO(); finalExit.setId(13L); finalExit.setStageCode("S6"); finalExit.setGateType("EXIT");
        var entry = new ProjectGateInstanceDO(); entry.setId(14L); entry.setStageCode("S6"); entry.setGateType("ENTRY");
        when(mapper.selectGatesForUpdate(any())).thenReturn(List.of(oldExit, skippedExit, finalExit, entry));
        assertEquals(List.of(11L, 13L), resolver.lockClosureGates(project).gates().stream().map(ProjectGateInstanceDO::getId).toList());
        assertEquals("DONE", stages.getFirst().getStatus());
    }
    @Test void terminalIsOnlyAReadResultNotProjectClosure() {
        stages.getFirst().setStatus("DONE"); stages.get(1).setStatus("DONE"); stages.getLast().setStatus("ACTIVE");
        project.setCurrentStage("S6");
        assertTrue(resolver.resolve(project).terminal()); assertEquals("ACTIVE", project.getLifecycleStatus());
        verify(mapper, never()).updateById(any(ProjectStageTransitionDO.class));
    }
    @Test void configuredClosureUsesFrozenTerminalMarkerWithoutRequiringS0() {
        configuredStages();
        assertFalse(resolver.resolveForClosure(project).terminal());
        stages.getFirst().setStatus("DONE"); stages.get(1).setStatus("DONE"); stages.getLast().setStatus("ACTIVE");
        project.setCurrentStage("S6");
        assertTrue(resolver.resolveForClosure(project).terminal());
        when(mapper.selectStages(any())).thenReturn(stages);
        assertTrue(resolver.inspectForClosure(project).terminal());
        assertEquals("ACTIVE", project.getLifecycleStatus());
        assertEquals("ACTIVE", stages.getLast().getStatus());
        // The original transition API retains its explicit legacy S0 contract.
        assertEquals(StageTransitionTargetResolver.Status.INVALID_GRAPH, resolver.resolve(project).transition().status());
    }
    @Test void configuredClosureStillRequiresCurrentPlanAndTenantIdentity() {
        configuredStages();
        project.setActivePlanVersionId(null);
        assertTrue(assertThrows(RuntimeException.class, () -> resolver.resolveForClosure(project)).getMessage().contains("GRAPH_PLAN_UNAVAILABLE"));
        project.setActivePlanVersionId(50L);
        stages.getLast().setTenantId(8L);
        assertThrows(RuntimeException.class, () -> resolver.resolveForClosure(project));
    }
    @Test void configuredClosureAcceptsCompletedTerminalGraphAndSkippedBranches() {
        configuredStages();
        stages.getFirst().setStatus("DONE"); stages.get(1).setStatus("SKIPPED"); stages.getLast().setStatus("DONE");
        project.setCurrentStage("S6");
        when(mapper.selectStages(any())).thenReturn(stages);

        assertTrue(resolver.resolveForClosure(project).terminal());
        assertTrue(resolver.inspectForClosure(project).terminal());
    }
    @Test void configuredClosureRejectsCompletedGraphWithActiveOrNonTerminalStage() {
        configuredStages();
        stages.getFirst().setStatus("DONE"); stages.get(1).setStatus("PENDING"); stages.getLast().setStatus("DONE");
        project.setCurrentStage("S6");
        assertTrue(assertThrows(RuntimeException.class, () -> resolver.resolveForClosure(project)).getMessage().contains("GRAPH_NODE_STALE"));

        stages.get(1).setStatus("ACTIVE");
        assertTrue(assertThrows(RuntimeException.class, () -> resolver.resolveForClosure(project)).getMessage().contains("GRAPH_NODE_STALE"));

        stages.get(1).setStatus("DONE");
        stages.getLast().setTerminalNode(false);
        assertTrue(assertThrows(RuntimeException.class, () -> resolver.resolveForClosure(project)).getMessage().contains("GRAPH_NODE_STALE"));
    }
    @Test void completedGraphIsStillRejectedByOrdinaryAndLegacyClosureResolution() {
        stages.getFirst().setStatus("DONE"); stages.get(1).setStatus("DONE"); stages.getLast().setStatus("DONE");
        project.setCurrentStage("S6");

        assertTrue(assertThrows(RuntimeException.class, () -> resolver.resolve(project)).getMessage().contains("GRAPH_NODE_STALE"));
        assertTrue(assertThrows(RuntimeException.class, () -> resolver.resolveForClosure(project)).getMessage().contains("GRAPH_NODE_STALE"));
    }
    private void configuredStages() {
        stages.getFirst().setCode("S1"); project.setCurrentStage("S1"); project.setActivePlanVersionId(50L);
        when(mapper.selectContracts(any())).thenReturn(stages.stream().map(stage -> {
            var contract = new ProjectStageExecutionContractDO(); contract.setTenantId(7L); contract.setProjectId(9L);
            contract.setStageId(stage.getId()); contract.setGraphVersion(1L); contract.setSourceNodeKey("stage-" + stage.getId());
            contract.setCompletionRuleSnapshot("{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}}");
            return contract;
        }).toList());
    }
    @Test void historyWithoutGraphFailsExplicitly() {
        stages.getFirst().setGraphVersion(null);
        assertTrue(assertThrows(RuntimeException.class, () -> resolver.resolve(project)).getMessage().contains("GRAPH_NOT_FROZEN"));
    }
    @Test void unknownConditionCannotSelectDefault() {
        var conditional = edge(1L, 2L); conditional.setConditionRuleRevisionId(101L);
        conditional.setConditionSnapshot(new FrozenDefinitions(SNAPSHOT).require(101L, "COMPLETION_RULE").toString());
        var fallback = edge(1L, 3L); fallback.setIsDefault(true);
        when(mapper.selectTransitions(any())).thenReturn(List.of(conditional, fallback, edge(2L, 3L)));
        assertEquals(StageTransitionTargetResolver.Status.UNAVAILABLE, resolver.resolve(project).transition().status());
    }
    @Test void staleTargetAndCrossTenantAreRejected() {
        stages.get(1).setStatus("DONE"); assertThrows(RuntimeException.class, () -> resolver.resolve(project));
        stages.get(1).setStatus("PENDING"); stages.get(1).setTenantId(8L);
        assertThrows(RuntimeException.class, () -> resolver.resolve(project));
    }
    @Test void nativeCompletionUsesRealTaskState() {
        var task = new ProjectTaskInstanceDO(); task.setStageCode("S0"); task.setStatus("IN_PROGRESS");
        when(mapper.selectTasksForUpdate(any())).thenReturn(List.of(task));
        assertEquals(StageTransitionTargetResolver.ConditionStatus.UNSATISFIED, resolver.resolve(project).completion());
        task.setStatus("DONE");
        assertEquals(StageTransitionTargetResolver.ConditionStatus.SATISFIED, resolver.resolve(project).completion());
    }
    @Test void anyDoesNotHideUnknownFact() {
        var evaluator = new ProjectRuntimeRuleEvaluator(new ProjectStageGateProviderRegistry(List.of(), mock(ProjectRuntimeGraphMapper.class), mock(ProjectNodeExecutionMapper.class)),
                new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleCompiler(), engine.evaluator(),
                mock(cn.iocoder.yudao.module.pms.project.service.rule.ProjectDecisionTableService.class),
                mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService.class), new cn.iocoder.yudao.module.pms.project.service.rule.ProjectRuleFields(key -> null));
        var rule = JsonUtils.parseObject("{\"operator\":\"ANY\",\"rules\":[{\"predicate\":\"STAGE_NATIVE_STATUS\",\"parameters\":{\"requiredStatus\":\"DONE\"}},{\"predicate\":\"PROCESS\",\"parameters\":{\"refCode\":\"unknown\"}}]}", tools.jackson.databind.JsonNode.class);
        assertEquals(StageTransitionTargetResolver.ConditionStatus.UNAVAILABLE, evaluator.evaluate(rule,
                new ProjectRuntimeRuleEvaluator.Facts(project, stages.getFirst(), List.of(), List.of(), List.of(), true)));
    }
    private ProjectStageInstanceDO stage(Long id, String code, int sort, boolean start, boolean terminal) {
        var s = new ProjectStageInstanceDO().setId(id).setProjectId(9L).setCode(code).setSortOrder(sort)
                .setStatus(start ? "ACTIVE" : "PENDING").setGraphVersion(1L).setStartNode(start).setTerminalNode(terminal)
                .setDefinitionRevisionId(id + 200);
        s.setTenantId(7L); return s;
    }
    private ProjectStageTransitionDO edge(Long from, Long to) {
        var e = new ProjectStageTransitionDO(); e.setTenantId(7L); e.setProjectId(9L); e.setGraphVersion(1L);
        e.setFromStageId(from); e.setToStageId(to); e.setTransitionCode("E" + from + to); e.setPriority(1); e.setIsDefault(false); return e;
    }
}
