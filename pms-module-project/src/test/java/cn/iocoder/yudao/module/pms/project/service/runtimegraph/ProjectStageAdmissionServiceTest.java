package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.runtimegraph.ProjectStageExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateReferenceInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.RuleEvaluation;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.stagegate.ProjectStageGateProviderRegistry;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import cn.iocoder.yudao.module.pms.project.service.projectplan.*;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectStageAdmissionServiceTest {
    private static RuleEngineTestFixture engine;
    @BeforeAll static void start() { engine = new RuleEngineTestFixture(); }
    @AfterAll static void stop() { engine.close(); }
    @AfterEach void clear() { TenantContextHolder.clear(); }

    private final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    private final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    private final ProjectStageInstanceMapper stages = mock(ProjectStageInstanceMapper.class);
    private final ProjectGateReferenceInstanceMapper references = mock(ProjectGateReferenceInstanceMapper.class);
    private final OperationAuditApi audit = mock(OperationAuditApi.class);
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper executions =
            mock(ProjectNodeExecutionMapper.class);
    private final ProjectRuleCompiler compiler = new ProjectRuleCompiler();
    private final cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper plans =
            mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectPlanVersionMapper.class);
    private final cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO plan =
            new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectPlanVersionDO();
    private final TemplateExecutionSnapshot effective = new TemplateExecutionSnapshot();
    private final List<ProjectStageInstanceDO> rows = new ArrayList<>();
    private final List<ProjectStageExecutionContractDO> contracts = new ArrayList<>();
    private ProjectMasterDO project;
    private ProjectStageAdmissionService service;

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE");
        project.setCurrentStage(null); project.setProjectName("eligible");
        project.setActivePlanVersionId(51L);
        plan.setId(51L);
        when(plans.selectEffective(any())).thenAnswer(call -> { plan.setExecutionSnapshot(JsonUtils.toJsonString(effective)); return plan; });
        when(executions.activateIfPending(any())).thenReturn(1);
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(graph.selectStagesForUpdate(any())).thenReturn(rows);
        when(graph.selectContracts(any())).thenReturn(contracts);
        when(graph.selectTasksForUpdate(any())).thenReturn(List.of());
        when(graph.selectGatesForUpdate(any())).thenReturn(List.of());
        when(stages.updateStatusIfMatch(any())).thenAnswer(call -> {
            var update = call.getArgument(0, cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageStatusUpdate.class);
            var row = rows.stream().filter(stage -> stage.getId().equals(update.stageId())).findFirst().orElseThrow();
            if (!row.getStatus().equals(update.expectedStatus()) || !row.getVersion().equals(update.expectedVersion())) return 0;
            row.setStatus(update.targetStatus()); row.setVersion(row.getVersion() + 1); return 1;
        });
        service = new ProjectStageAdmissionService(projects, graph, stages, references,
                new ProjectRuntimeRuleEvaluator(new ProjectStageGateProviderRegistry(List.of(), mock(ProjectRuntimeGraphMapper.class), mock(ProjectNodeExecutionMapper.class)), compiler, engine.evaluator(),
                        mock(ProjectDecisionTableService.class), mock(cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectBusinessFactSourceService.class)), compiler, audit, executions, plans);
    }

    @Test void activatesIndependentStagesAndDoesNotReleaseUnknownOrFalseBranches() {
        add("DISCOVERY", null);
        add("DELIVERY", "{\"predicate\":\"FIELD\",\"parameters\":{\"fieldCode\":\"project.projectName\",\"valueType\":\"TEXT\",\"operator\":\"=\",\"value\":\"eligible\"}}");
        add("APPROVAL_WAIT", "{\"operator\":\"NOT\",\"rules\":[{\"predicate\":\"APPROVAL\",\"parameters\":{\"refCode\":\"approval\"}}]}");
        add("OPTIONAL", "{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}");
        var result = service.activateEligible(9L, 11L, "creation");
        assertEquals(List.of("ACTIVE", "ACTIVE", "PENDING", "PENDING"), rows.stream().map(ProjectStageInstanceDO::getStatus).toList());
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, result.get(2).outcome());
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, result.get(3).outcome());
        verify(stages, times(2)).updateStatusIfMatch(any());
        verify(audit, times(2)).record(eq(7L), eq(11L), eq("creation"), eq("PROJECT_STAGE_ACTIVATED"),
                eq("PROJECT_STAGE"), anyString(), eq("SUCCESS"), anyMap());
        assertNull(project.getCurrentStage());
        verifyNoInteractions(references);
    }

    @Test void replayDoesNotActivateOrAuditAlreadyActiveStages() {
        add("FIRST", null); add("SECOND", null);
        service.activateEligible(9L, 11L, "event");
        assertTrue(service.activateEligible(9L, 11L, "event").isEmpty());
        verify(stages, times(2)).updateStatusIfMatch(any());
    }

    @Test void staleContractOnlyBlocksItsOwnBranch() {
        add("STALE", null); add("INDEPENDENT", null);
        contracts.getFirst().setGraphVersion(2L);
        var result = service.activateEligible(9L, 11L, "event");
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, result.getFirst().outcome());
        assertEquals("ACTIVE", rows.getLast().getStatus());
        verify(stages).updateStatusIfMatch(any());
    }

    @Test void missingFrozenProgramCannotFallbackToAnotherRuleOrCurrentTemplate() {
        add("WAIT", "{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}");
        effective.getRulePrograms().clear();
        assertEquals(RuleEvaluation.Outcome.UNKNOWN, service.activateEligible(9L, 11L, "event").getFirst().outcome());
        verifyNoInteractions(stages, audit);
    }

    @Test void closedProjectDoesNotRunRulesOrAdvanceStages() {
        project.setLifecycleStatus("NORMAL_CLOSED");
        assertTrue(service.activateEligible(9L, 11L, "event").isEmpty());
        verifyNoInteractions(graph, stages, audit);
    }

    @Test void stateConflictIsNotSwallowedAsAnUnknownRule() {
        add("FIRST", null); doReturn(0).when(stages).updateStatusIfMatch(any());
        assertThrows(IllegalStateException.class, () -> service.activateEligible(9L, 11L, "event"));
        verifyNoInteractions(audit);
    }

    @Test void changedProjectPlanRulesDoNotRequireRebindingTheStageBusinessContract() {
        add("WAIT", "{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}");
        String bindingSnapshot = contracts.getFirst().getDefinitionSnapshot();
        effective.getRulePrograms().put("admit_WAIT", compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}")));
        plan.setId(52L); project.setActivePlanVersionId(52L);
        assertEquals(RuleEvaluation.Outcome.NOT_MATCHED, service.activateEligible(9L,11L,"plan-change").getFirst().outcome());
        assertEquals(bindingSnapshot,contracts.getFirst().getDefinitionSnapshot());
        assertEquals(101L,contracts.getFirst().getId()); verifyNoInteractions(stages,audit);
    }

    @Test void mismatchedPlanPointerCannotUseRulesFromAnotherVersion() {
        add("WAIT", "{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}");
        plan.setId(99L);
        assertEquals("PROJECT_PLAN_VERSION_UNAVAILABLE",service.activateEligible(9L,11L,"event").getFirst().reasonCode());
        verifyNoInteractions(stages,audit);
    }

    @Test void taskStartNeedsItsActiveStageAndItsOwnFrozenAdmission() {
        add("DISCOVERY", null);
        var task = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO()
                .setId(21L).setProjectId(9L).setTaskCode("WORK").setStageCode("DISCOVERY");
        var taskContract = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO();
        taskContract.setId(201L); taskContract.setSourceNodeKey("task:work"); taskContract.setContractVersion(1);
        var snapshot = effective;
        var definition = new TemplateExecutionSnapshot.TaskContract(); definition.setNodeKey("task:work");
        definition.setCode("WORK"); definition.setStageCode("DISCOVERY"); definition.setAdmissionRuleKey("task-admit");
        snapshot.setTasks(List.of(definition));
        snapshot.getRulePrograms().put("task-admit", compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":true}}")));
        assertFalse(service.taskMayStart(project, task, taskContract));
        rows.getFirst().setStatus("ACTIVE");
        assertTrue(service.taskMayStart(project, task, taskContract));
        snapshot.getRulePrograms().put("task-admit", compiler.compile(JsonUtils.parseTree("{\"predicate\":\"CONSTANT\",\"parameters\":{\"value\":false}}")));
        assertFalse(service.taskMayStart(project, task, taskContract));
        snapshot.getRulePrograms().clear();
        assertFalse(service.taskMayStart(project, task, taskContract));
        verifyNoInteractions(stages, audit);
    }

    @Test void elapsedTimeWaitsForStageThenOrdinaryReevaluationAdmitsThroughNativeLiteFlow() {
        add("PREP", null);
        var task = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO()
                .setId(21L).setProjectId(9L).setTaskCode("SURVEY").setStageCode("PREP").setStatus("PENDING_START");
        task.setTenantId(7L);
        var contract = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO();
        contract.setId(201L); contract.setProjectTaskId(21L); contract.setTenantId(7L); contract.setSourceNodeKey("task:survey");
        var definition = new TemplateExecutionSnapshot.TaskContract(); definition.setNodeKey("task:survey");
        definition.setCode("SURVEY"); definition.setStageCode("PREP"); definition.setAdmissionRuleKey("time");
        effective.setTasks(List.of(definition));
        effective.getRulePrograms().put("time", compiler.compile(JsonUtils.parseTree(
                "{\"predicate\":\"TIME_REACHED\",\"parameters\":{\"at\":\"2020-01-01T00:00:00Z\"}}")));
        var round = new cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.ProjectNodeExecutionDO();
        round.setId(301L); round.setNodeKind("TASK"); round.setNodeInstanceId(21L); round.setNodeKey("task:survey");
        round.setContractId(201L); round.setPlanVersionId(51L); round.setStatus("PENDING");
        var taskContracts = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper.class);
        when(taskContracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(projects.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        var command = new ProjectTaskAdmissionService(projects, taskContracts, executions, service, audit);
        assertFalse(command.activateEligible(9L, 21L, "time-before-stage").activated());
        verify(executions, never()).activateIfPending(any());
        assertTrue(service.activateEligible(9L, null, "stage-reevaluation").getFirst().activated());
        assertTrue(command.activateEligible(9L, 21L, "ordinary-reevaluation").activated());
        assertEquals("PENDING_START", task.getStatus()); assertNull(task.getActualStartTime());
        verify(executions).activateIfPending(argThat(write -> "TASK".equals(write.nodeKind()) && write.nodeInstanceId().equals(21L)));
    }

    @Test void ordinaryReevaluationRollsBackOnlyFailedStageAndCommitsIndependentBranches() {
        add("FIRST", null); add("FAILED", null); add("LAST", null);
        // Real Spring transactions over an isolated H2 ledger; production MySQL mapper SQL is not exercised.
        var database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        try {
            var jdbc = new JdbcTemplate(database);
            jdbc.execute("CREATE TABLE admission_ledger (id BIGINT PRIMARY KEY, stage_status VARCHAR(20), round_status VARCHAR(20), version INT)");
            for (var stage : rows) jdbc.update("INSERT INTO admission_ledger VALUES (?,'PENDING','PENDING',0)", stage.getId());
            org.mockito.stubbing.Answer<List<ProjectStageInstanceDO>> refresh = call -> {
                for (var stage : rows) {
                    stage.setStatus(jdbc.queryForObject("SELECT stage_status FROM admission_ledger WHERE id=?", String.class, stage.getId()));
                    stage.setVersion(jdbc.queryForObject("SELECT version FROM admission_ledger WHERE id=?", Integer.class, stage.getId()));
                }
                return rows;
            };
            when(graph.selectStages(any())).thenAnswer(refresh);
            when(graph.selectStagesForUpdate(any())).thenAnswer(refresh);
            doAnswer(call -> {
                var write = call.getArgument(0, cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.ProjectStageStatusUpdate.class);
                return jdbc.update("UPDATE admission_ledger SET stage_status='ACTIVE',version=version+1 WHERE id=? AND stage_status='PENDING'", write.stageId());
            }).when(stages).updateStatusIfMatch(any());
            when(executions.activateIfPending(any())).thenAnswer(call -> {
                var write = call.getArgument(0, cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.ProjectNodeExecutionMapper.Activation.class);
                return jdbc.update("UPDATE admission_ledger SET round_status='ACTIVE' WHERE id=? AND round_status='PENDING'", write.nodeInstanceId());
            });
            doThrow(new IllegalStateException("second-stage audit failed")).when(audit).record(eq(7L), eq(11L), eq("event"),
                    eq("PROJECT_STAGE_ACTIVATED"), eq("PROJECT_STAGE"), eq("2"), eq("SUCCESS"), anyMap());
            var factory = new ProxyFactory(service);
            factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(database), new AnnotationTransactionAttributeSource()));
            var admission = (ProjectStageAdmissionService) factory.getProxy();
            var completion = mock(ProjectStageCompletionService.class);
            when(completion.completeStage(anyLong(), anyLong(), anyLong(), anyString())).thenReturn(new ProjectStageCompletionService.Completion(0, false));
            var tasks = mock(ProjectBusinessTaskCompletionService.class);
            when(tasks.completeEligible(9L, "event")).thenReturn(new ProjectBusinessTaskCompletionService.Result(0, 0, false));
            var closure = mock(ProjectRuleClosureService.class);
            when(closure.closeIfSatisfied(9L, 11L, "event")).thenReturn(new ProjectRuleClosureService.Closure(false, false));
            var projectReader = mock(cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper.class);
            when(projectReader.selectById(9L)).thenReturn(project);
            var coordinator = new ProjectRuntimeCoordinator(admission, completion, closure, tasks,
                    mock(ProjectGateRuleService.class), graph, mock(ProjectTaskBusinessAssociationService.class), projectReader);
            var result = coordinator.reevaluate(9L, 11L, "event");
            assertTrue(result.unknown()); assertEquals(2, result.activated());
            assertEquals(List.of("ACTIVE", "PENDING", "ACTIVE"), jdbc.queryForList("SELECT stage_status FROM admission_ledger ORDER BY id", String.class));
            assertEquals(List.of("ACTIVE", "PENDING", "ACTIVE"), jdbc.queryForList("SELECT round_status FROM admission_ledger ORDER BY id", String.class));
            assertEquals(List.of(1, 0, 1), jdbc.queryForList("SELECT version FROM admission_ledger ORDER BY id", Integer.class));
            assertEquals(0, coordinator.reevaluate(9L, 11L, "event").activated());
            verify(audit).record(eq(7L), eq(11L), eq("event"), eq("PROJECT_STAGE_ACTIVATED"), eq("PROJECT_STAGE"), eq("1"), eq("SUCCESS"), anyMap());
            verify(audit).record(eq(7L), eq(11L), eq("event"), eq("PROJECT_STAGE_ACTIVATED"), eq("PROJECT_STAGE"), eq("3"), eq("SUCCESS"), anyMap());
            verify(completion, never()).completeStage(9L, 2L, 11L, "event");
        } finally { database.shutdown(); }
    }

    private void add(String code, String expression) {
        long id = rows.size() + 1L;
        var stage = new ProjectStageInstanceDO().setId(id).setProjectId(9L).setStageCode(code).setStatus("PENDING").setVersion(0).setGraphVersion(1L);
        stage.setTenantId(7L); rows.add(stage);
        var definition = new TemplateExecutionSnapshot.StageContract(); definition.setNodeKey("stage:" + code); definition.setCode(code);
        var snapshot = new TemplateExecutionSnapshot(); snapshot.setStages(List.of(definition));
        effective.getStages().add(definition);
        if (expression != null) {
            definition.setAdmissionRuleKey("admit_" + code);
            var program = compiler.compile(JsonUtils.parseTree(expression));
            snapshot.getRulePrograms().put(definition.getAdmissionRuleKey(), program);
            effective.getRulePrograms().put(definition.getAdmissionRuleKey(), program);
        }
        var contract = new ProjectStageExecutionContractDO(); contract.setId(100L + id); contract.setStageId(id);
        contract.setTenantId(7L); contract.setProjectId(9L); contract.setSourceNodeKey(definition.getNodeKey()); contract.setGraphVersion(1L);
        contract.setDefinitionSnapshot(JsonUtils.toJsonString(snapshot)); contracts.add(contract);
    }
}
