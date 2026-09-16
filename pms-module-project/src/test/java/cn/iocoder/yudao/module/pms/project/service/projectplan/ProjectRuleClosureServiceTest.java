package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuntimeRuleEvaluator;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectRuleClosureServiceTest {
    static RuleEngineTestFixture engine;
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    final OperationAuditApi audit = mock(OperationAuditApi.class);
    final ProjectRuleCompiler compiler = new ProjectRuleCompiler();
    final ProjectGateReferenceInstanceMapper references = mock(ProjectGateReferenceInstanceMapper.class);
    final cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi processes = mock(cn.iocoder.yudao.module.pms.project.api.stagegate.ProjectStageGateProcessOwnerApi.class);
    ProjectRuleClosureService service;
    ProjectPlanVersionDO plan;
    TemplateExecutionSnapshot snapshot;
    ProjectMasterDO project;
    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE"); project.setVersion(3);
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        snapshot = new TemplateExecutionSnapshot(); snapshot.setClosureRuleKey("close");
        snapshot.getRulePrograms().put("close", compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}")));
        plan = new ProjectPlanVersionDO(); plan.setId(21L); plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        when(plans.selectEffective(any())).thenReturn(plan);
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(new ProjectStageInstanceDO().setStatus("DONE")));
        service = new ProjectRuleClosureService(projects, plans, executions, graph, references,
                new ProjectRuntimeRuleEvaluator(new ProjectStageGateProviderRegistry(List.of(), mock(ProjectRuntimeGraphMapper.class), mock(ProjectNodeExecutionMapper.class)), compiler, engine.evaluator(), mock(ProjectDecisionTableService.class), mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService.class)), audit, processes);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "childWaitEvents",
                mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectChildWaitEvents.class));
    }
    @Test void explicitClosureRuleClosesOnlyThisProjectWithFrozenEvidence() {
        when(plans.closeProjectIfActive(any())).thenReturn(1); when(plans.recordClosureIfOpen(any())).thenReturn(1);
        assertTrue(service.closeIfSatisfied(9L, 1L, "test").closed());
        verify(plans).closeProjectIfActive(argThat(update -> update.projectId().equals(9L) && update.expectedProjectVersion()==3 && update.planVersionId().equals(21L)));
        verify(plans).recordClosureIfOpen(argThat(update -> update.evidence().contains("pmsRuleMatched")));
        var events = (cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectChildWaitEvents)
                org.springframework.test.util.ReflectionTestUtils.getField(service, "childWaitEvents");
        verify(events).closureChanged(7L, 9L, 1L, "test");
    }
    @Test void childWaitCannotBypassTheParentsOwnClosureCondition() {
        var childWait = mock(cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectChildWaitFacts.class);
        var runtime = org.springframework.test.util.ReflectionTestUtils.getField(service, "rules");
        org.springframework.test.util.ReflectionTestUtils.setField(runtime, "childWait", childWait);
        when(childWait.resolve(any(), any())).thenReturn(cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact.known(true));
        var condition = JsonUtils.parseTree("""
                {"operator":"ALL","rules":[
                  {"predicate":"CHILD_PROJECT_WAIT","parameters":{"scope":"DIRECT","acceptedClosureTypes":["NORMAL_CLOSED","EXCEPTION_CLOSED"],"quantifier":"ALL","emptyResult":true}},
                  {"predicate":"CONSTANT","parameters":{"value":false}}
                ]}
                """);
        snapshot.getRulePrograms().put("close", compiler.compile(condition));
        plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        assertFalse(service.closeIfSatisfied(9L, 1L, "wait").closed());
        verify(plans, never()).closeProjectIfActive(any());
        when(childWait.resolve(any(), any())).thenReturn(cn.iocoder.yudao.module.pms.project.domain.rule.RuleFact.unknown("CHILD_CLOSURE_FACT_UNAVAILABLE"));
        assertTrue(service.closeIfSatisfied(9L, 1L, "wait").unknown());
    }

    @Test void timerClosureUsesSystemAuditActorWithoutInventingAnInteractiveActor() {
        when(plans.closeProjectIfActive(any())).thenReturn(1); when(plans.recordClosureIfOpen(any())).thenReturn(1);
        assertTrue(service.closeIfSatisfied(9L, null, "timer").closed());
        verify(audit).record(eq(7L), eq(0L), eq("timer"), eq("PROJECT_CLOSED_BY_RULE"), eq("Project"), eq("9"), eq("SUCCESS"), anyMap());
    }
    @Test void terminalStageDoesNotSupplyAnImplicitClosureRule() {
        snapshot.setClosureRuleKey(null); plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        assertFalse(service.closeIfSatisfied(9L, 1L, "test").closed());
        verify(plans, never()).closeProjectIfActive(any());
    }

    @Test void optionalPendingStageStopsClosureOnlyWhileItsEntryProcessIsStillRunning() {
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(new ProjectStageInstanceDO().setStatus("PENDING")));
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of(new ProjectGateInstanceDO().setId(51L)));
        when(references.selectOrderedForUpdate(any())).thenReturn(List.of(new ProjectGateReferenceInstanceDO().setId(52L).setGateId(51L).setRefType("APPROVAL")));
        when(processes.inspectRunning(any())).thenReturn(List.of(new cn.iocoder.yudao.module.pms.project.api.stagegate.dto.ProjectStageGateRunningProcess("pi",52L,"PREP")));
        var waiting=service.closeIfSatisfied(9L,1L,"waiting"); assertFalse(waiting.closed()); assertFalse(waiting.unknown());
        when(processes.inspectRunning(any())).thenThrow(new IllegalStateException("unavailable"));
        assertTrue(service.closeIfSatisfied(9L,1L,"unknown").unknown()); verify(plans,never()).closeProjectIfActive(any());
        doReturn(List.of()).when(processes).inspectRunning(any());
        when(plans.closeProjectIfActive(any())).thenReturn(1); when(plans.recordClosureIfOpen(any())).thenReturn(1);
        assertTrue(service.closeIfSatisfied(9L,1L,"ended").closed());
    }
    @Test void activeStageAndStartedTasksCannotBeAbandoned() {
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(new ProjectStageInstanceDO().setStatus("ACTIVE")));
        assertFalse(service.closeIfSatisfied(9L, 1L, "test").closed());
        when(graph.selectStagesForUpdate(any())).thenReturn(List.of(new ProjectStageInstanceDO().setStatus("DONE")));
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(new ProjectTaskInstanceDO().setId(71L).setStatus("IN_PROGRESS")));
        assertFalse(service.closeIfSatisfied(9L, 1L, "test").closed());
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(new ProjectTaskInstanceDO().setId(71L).setStatus("PENDING_START")));
        var started = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO();
        started.setStartedAt(java.time.LocalDateTime.now()); started.setStatus("ACTIVE");
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(started));
        assertFalse(service.closeIfSatisfied(9L, 1L, "test").closed());
        verify(plans, never()).closeProjectIfActive(any());
    }

    @Test void automaticReferenceToUnstartedOptionalTaskDoesNotAddAClosureRequirement() {
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of(new ProjectTaskInstanceDO().setId(71L).setStatus("PENDING_ASSIGN")));
        when(plans.closeProjectIfActive(any())).thenReturn(1); when(plans.recordClosureIfOpen(any())).thenReturn(1);
        assertTrue(service.closeIfSatisfied(9L,1L,"optional-branch").closed());
    }
    @Test void unknownConditionCannotBecomeClosureAndClosedProjectDoesNotRepeat() {
        snapshot.getRulePrograms().put("close", compiler.compile(JsonUtils.parseTree("{\"operator\":\"NOT\",\"rules\":[{\"predicate\":\"APPROVAL\",\"parameters\":{\"refCode\":\"approval\"}}]}")));
        plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot));
        assertTrue(service.closeIfSatisfied(9L, 1L, "test").unknown());
        verify(plans, never()).closeProjectIfActive(any());
        project.setLifecycleStatus("NORMAL_CLOSED");
        assertFalse(service.closeIfSatisfied(9L, 1L, "test").closed());
        verifyNoInteractions(audit);
    }
}
