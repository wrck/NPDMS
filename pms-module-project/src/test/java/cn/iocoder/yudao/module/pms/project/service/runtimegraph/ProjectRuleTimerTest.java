package cn.iocoder.yudao.module.pms.project.service.runtimegraph;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectplan.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.domain.rule.*;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateExecutionSnapshot;
import cn.iocoder.yudao.module.pms.project.service.projectplan.*;
import cn.iocoder.yudao.module.pms.project.service.rule.*;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectRuleTimerTest {
    final ProjectTaskRuntimeMapper projects = mock(ProjectTaskRuntimeMapper.class);
    final ProjectPlanVersionMapper plans = mock(ProjectPlanVersionMapper.class);
    final ProjectNodeExecutionMapper executions = mock(ProjectNodeExecutionMapper.class);
    final ProjectTaskExecutionContractMapper contracts = mock(ProjectTaskExecutionContractMapper.class);
    final ProjectStageAdmissionService admission = mock(ProjectStageAdmissionService.class);
    final ProjectStageCompletionService stages = mock(ProjectStageCompletionService.class);
    final ProjectTaskLifecycleService tasks = mock(ProjectTaskLifecycleService.class);
    final ProjectRuleClosureService closure = mock(ProjectRuleClosureService.class);
    final PlatformBusinessEventApi events = mock(PlatformBusinessEventApi.class);
    final OperationAuditApi audit = mock(OperationAuditApi.class);
    final ProjectTaskAdmissionService taskAdmission = new ProjectTaskAdmissionService(projects, contracts, executions, admission, audit);
    final ProjectRuleTimerDelivery service = new ProjectRuleTimerDelivery(projects, plans, executions, taskAdmission, admission, stages, tasks, closure, events);
    final Instant due = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    ProjectMasterDO project;
    ProjectNodeExecutionDO round;
    TemplateExecutionSnapshot snapshot;
    ProjectPlanVersionDO plan;

    @BeforeEach void setup() {
        org.springframework.test.util.ReflectionTestUtils.setField(taskAdmission, "timers", mock(ProjectRuleTimerScheduler.class));
        org.springframework.test.util.ReflectionTestUtils.setField(taskAdmission, "lifecycle", tasks);
        TenantContextHolder.setTenantId(7L);
        project = new ProjectMasterDO(); project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE"); project.setActivePlanVersionId(51L);
        when(projects.selectProjectForCommandForUpdate(any())).thenReturn(project);
        round = new ProjectNodeExecutionDO(); round.setId(61L); round.setTenantId(7L); round.setProjectId(9L); round.setPlanVersionId(51L);
        round.setNodeKey("prep"); round.setNodeKind("STAGE"); round.setNodeInstanceId(11L); round.setContractId(21L); round.setCurrentMarker(1); round.setStatus("PENDING");
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round));
        when(executions.selectCurrent(any())).thenReturn(List.of(round));
        snapshot = new TemplateExecutionSnapshot();
        var stage = new TemplateExecutionSnapshot.StageContract(); stage.setNodeKey("prep"); stage.setAdmissionRuleKey("time");
        stage.setCompletionRuleKey("time"); stage.setExitRuleKey("time"); snapshot.setStages(List.of(stage));
        snapshot.setClosureRuleKey("time");
        snapshot.setRulePrograms(Map.of("time", new ProjectRuleCompiler().compile(JsonUtils.parseTree(
                "{\"predicate\":\"TIME_REACHED\",\"parameters\":{\"at\":\"" + due + "\"}}"))));
        plan = new ProjectPlanVersionDO(); plan.setId(51L); saveSnapshot();
        when(plans.selectEffective(any())).thenReturn(plan);
    }
    @AfterEach void clear() { TenantContextHolder.clear(); }
    void saveSnapshot() { plan.setExecutionSnapshot(JsonUtils.toJsonString(snapshot)); }
    ProjectRuleTimer timer(ProjectRuleTimer.Purpose purpose) {
        return ProjectRuleTimer.create(7L, 9L, 51L, purpose == ProjectRuleTimer.Purpose.CLOSURE ? null : 61L,
                purpose == ProjectRuleTimer.Purpose.CLOSURE ? List.of(61L) : List.of(), purpose, "time", due, null);
    }

    @Test void schedulesFrozenOneShotEventsForEverySlotAndOnlyNewReworkRounds() {
        var scheduler = new ProjectRuleTimerScheduler(executions, events, plans);
        scheduler.schedule(9L, 51L, snapshot, null);
        var event = ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent.class);
        verify(events, times(4)).appendAt(eq("ProjectPlan"), eq("51"), event.capture(), eq(LocalDateTime.ofInstant(due, ZoneId.systemDefault())));
        Set<ProjectRuleTimer.Purpose> purposes = new HashSet<>();
        for (var message : event.getAllValues()) {
            var restored = JsonUtils.parseObject(message.eventPayload(), ProjectRuleTimer.class);
            assertEquals(message.eventId(), restored.eventId()); assertTrue(message.eventId().length() <= 64);
            assertEquals(51L, restored.planVersionId()); assertEquals(due, restored.dueAt()); purposes.add(restored.purpose());
        }
        assertEquals(Set.of(ProjectRuleTimer.Purpose.values()), purposes);
        clearInvocations(events);
        scheduler.schedule(9L, 51L, snapshot, Set.of(999L));
        verify(events).appendAt(eq("ProjectPlan"), eq("51"), any(), any()); // Only the project closure receives its new round-set scope.
    }

    @Test void obsoletePlanReworkRoundAndClosedProjectCannotInvokeStateCommands() {
        var timer = timer(ProjectRuleTimer.Purpose.ADMISSION);
        project.setActivePlanVersionId(52L); assertTrue(service.deliver(timer));
        project.setActivePlanVersionId(51L); round.setId(62L); assertTrue(service.deliver(timer));
        round.setId(61L); round.setPlanVersionId(52L); assertTrue(service.deliver(timer));
        round.setPlanVersionId(51L); round.setStatus("DONE"); assertTrue(service.deliver(timer));
        round.setStatus("PENDING"); project.setLifecycleStatus("CLOSED"); assertTrue(service.deliver(timer));
        verifyNoInteractions(admission, stages, tasks, closure, events);
    }

    @Test void validAdmissionOnlyAdvancesItsTargetAndDuplicateDeliveryDoesNotAdvanceTwice() {
        when(admission.activateStage(eq(9L), isNull(), anyString(), eq(11L))).thenAnswer(call -> {
            if ("ACTIVE".equals(round.getStatus())) return List.of();
            round.setStatus("ACTIVE");
            return List.of(new ProjectStageAdmissionService.StageAdmission(11L,"prep", RuleEvaluation.Outcome.MATCHED,null,true));
        });
        var event = timer(ProjectRuleTimer.Purpose.ADMISSION);
        assertTrue(service.deliver(event)); assertTrue(service.deliver(event));
        verify(events).append(eq("Project"), eq("9"), any());
        verifyNoInteractions(stages, tasks, closure);
    }

    @Test void unknownRetriesAndFailureCannotSkipToOtherNodes() {
        when(stages.completeStage(eq(9L), eq(11L), isNull(), anyString())).thenReturn(new ProjectStageCompletionService.Completion(0,true));
        assertFalse(service.deliver(timer(ProjectRuleTimer.Purpose.COMPLETION)));
        when(stages.completeStage(eq(9L), eq(11L), isNull(), anyString())).thenThrow(new IllegalStateException("owner unavailable"));
        assertThrows(IllegalStateException.class, () -> service.deliver(timer(ProjectRuleTimer.Purpose.EXIT)));
        verifyNoInteractions(events, tasks, closure, admission);
    }

    @Test void missingEffectivePlanIsUnknownNotAnObsoleteTimer() {
        when(plans.selectEffective(any())).thenReturn(null);
        assertFalse(service.deliver(timer(ProjectRuleTimer.Purpose.COMPLETION)));
        verifyNoInteractions(events, tasks, stages, closure, admission);
    }

    @Test void closureIsBoundToAllCurrentRoundsButNotTheirChangingStatus() {
        var event = timer(ProjectRuleTimer.Purpose.CLOSURE);
        round.setStatus("DONE");
        when(closure.closeIfSatisfied(eq(9L), isNull(), anyString())).thenReturn(new ProjectRuleClosureService.Closure(true,false));
        assertTrue(service.deliver(event));
        round.setId(62L); assertTrue(service.deliver(event));
        verify(closure).closeIfSatisfied(eq(9L), isNull(), anyString());
        verifyNoInteractions(events, tasks, stages, admission);
    }

    @Test void earlyForeignTenantAndWrongFrozenRuleAreNotExecuted() {
        var early = ProjectRuleTimer.create(7L,9L,51L,61L,List.of(),ProjectRuleTimer.Purpose.ADMISSION,"time",Instant.now().plusSeconds(60),null);
        assertFalse(service.deliver(early));
        TenantContextHolder.setTenantId(8L);
        assertThrows(IllegalArgumentException.class, () -> service.deliver(timer(ProjectRuleTimer.Purpose.ADMISSION)));
        TenantContextHolder.setTenantId(7L);
        snapshot.getStages().getFirst().setAdmissionRuleKey("other"); saveSnapshot();
        assertThrows(IllegalArgumentException.class, () -> service.deliver(timer(ProjectRuleTimer.Purpose.ADMISSION)));
        verifyNoInteractions(events, tasks, stages, admission, closure);
    }

    @Test void taskAdmissionPreservesUnknownAndStartsOnlyTheFormalProjectTaskLifecycle() {
        round.setNodeKind("TASK");
        var node = new TemplateExecutionSnapshot.TaskContract(); node.setNodeKey("prep"); node.setAdmissionRuleKey("time");
        snapshot.setTasks(List.of(node)); snapshot.setStages(List.of()); saveSnapshot();
        var task = new ProjectTaskInstanceDO(); task.setId(11L); task.setTenantId(7L); task.setProjectId(9L);
        var contract = new ProjectTaskExecutionContractDO(); contract.setId(21L); contract.setTenantId(7L);
        contract.setProjectTaskId(11L); contract.setSourceNodeKey("prep");
        when(projects.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(admission.taskAdmissionFact(project,task,contract)).thenReturn(RuleFact.unknown("OWNER_UNAVAILABLE"));
        assertFalse(service.deliver(timer(ProjectRuleTimer.Purpose.ADMISSION)));
        when(admission.taskAdmissionFact(project,task,contract)).thenReturn(RuleFact.known(false));
        assertTrue(service.deliver(timer(ProjectRuleTimer.Purpose.ADMISSION)));
        verify(executions, never()).activateIfPending(any());
        verifyNoInteractions(tasks);
        when(admission.taskAdmissionFact(project,task,contract)).thenReturn(RuleFact.known(true));
        when(executions.activateIfPending(any())).thenReturn(1);
        var admissionTimer = timer(ProjectRuleTimer.Purpose.ADMISSION);
        assertTrue(service.deliver(admissionTimer));
        verify(executions).activateIfPending(argThat(write -> write.planVersionId()==51L && write.nodeInstanceId()==11L && write.nodeKind().equals("TASK")));
        verify(tasks).startAdmittedTask(eq(project), eq(task), eq(contract), eq(admissionTimer.eventId()));
        verifyNoMoreInteractions(tasks);
        verifyNoInteractions(stages, closure);
    }

    @Test void relativeActivationRegistersOnActivationAndBindsTheActualExecution() {
        var scheduler = new ProjectRuleTimerScheduler(executions, events, plans);
        snapshot.getStages().getFirst().setAdmissionRuleKey(null);
        snapshot.getStages().getFirst().setExitRuleKey(null); snapshot.setClosureRuleKey(null);
        snapshot.setRulePrograms(Map.of("time", new ProjectRuleCompiler().compile(JsonUtils.parseTree("""
                {"predicate":"WAIT_ELAPSED","parameters":{"anchor":"NODE_ACTIVATED","duration":"PT30M"}}
                """)))); saveSnapshot();
        scheduler.schedule(9L, 51L, snapshot, null); verifyNoInteractions(events);
        round.setStatus("ACTIVE"); round.setAdmittedAt(LocalDateTime.ofInstant(due.minusSeconds(1800), ZoneId.systemDefault()));
        scheduler.scheduleFromNode(9L, "STAGE", 11L);
        var emitted = capturedTimers(1).getFirst();
        assertEquals(due, emitted.dueAt()); assertEquals(61L, emitted.anchorExecutionId()); assertEquals(61L, emitted.executionId());
        when(stages.completeStage(eq(9L), eq(11L), isNull(), anyString())).thenReturn(new ProjectStageCompletionService.Completion(1, false));
        assertTrue(service.deliver(emitted));
        verify(stages).completeStage(eq(9L), eq(11L), isNull(), eq(emitted.eventId()));
    }

    @Test void sourceCompletionRegistersDependentAndClosureTimersAndOldSourceRoundCannotAdvanceEither() {
        var scheduler = new ProjectRuleTimerScheduler(executions, events, plans);
        var source = completedSource();
        relativeSourceRule(source);
        source.setStatus("ACTIVE"); source.setEndedAt(null);
        scheduler.schedule(9L, 51L, snapshot, null); verifyNoInteractions(events);
        source.setStatus("DONE"); source.setEndedAt(LocalDateTime.ofInstant(due.minusSeconds(1800), ZoneId.systemDefault()));
        scheduler.scheduleFromNode(9L, "TASK", 12L);
        var emitted = capturedTimers(2);
        assertEquals(Set.of(ProjectRuleTimer.Purpose.ADMISSION, ProjectRuleTimer.Purpose.CLOSURE), emitted.stream().map(ProjectRuleTimer::purpose).collect(java.util.stream.Collectors.toSet()));
        assertTrue(emitted.stream().allMatch(timer -> timer.anchorExecutionId().equals(62L) && timer.dueAt().equals(due)));
        // The consumer stays in its current round while only the selected source is reworked.
        source.setId(63L); source.setStatus("ACTIVE"); source.setEndedAt(null);
        for (var timer : emitted) assertTrue(service.deliver(timer));
        verifyNoInteractions(admission, stages, tasks, closure);
        clearInvocations(events);
        scheduler.scheduleFromNode(9L, "TASK", 12L); verifyNoInteractions(events);
        source.setStatus("DONE"); source.setEndedAt(LocalDateTime.ofInstant(due.minusSeconds(900), ZoneId.systemDefault()));
        scheduler.scheduleFromNode(9L, "TASK", 12L);
        assertTrue(capturedTimers(2).stream().allMatch(timer -> timer.anchorExecutionId().equals(63L) && timer.dueAt().equals(due.plusSeconds(900))));
    }

    @Test void reworkedConsumerCanWaitOnUnselectedCompletedHistoryAndDeliveryReevaluatesOnlyItsRule() {
        var source = completedSource(); source.setPlanVersionId(50L); relativeSourceRule(source);
        var scheduler = new ProjectRuleTimerScheduler(executions, events, plans);
        scheduler.schedule(9L, 51L, snapshot, Set.of(61L));
        var timer = capturedTimers(2).stream().filter(event -> event.purpose() == ProjectRuleTimer.Purpose.ADMISSION).findFirst().orElseThrow();
        when(admission.activateStage(eq(9L), isNull(), anyString(), eq(11L))).thenReturn(List.of(
                new ProjectStageAdmissionService.StageAdmission(11L, "prep", RuleEvaluation.Outcome.UNKNOWN, "OWNER_UNAVAILABLE", false)));
        assertFalse(service.deliver(timer));
        when(admission.activateStage(eq(9L), isNull(), anyString(), eq(11L))).thenAnswer(call -> {
            if ("ACTIVE".equals(round.getStatus())) return List.of();
            round.setStatus("ACTIVE"); return List.of(new ProjectStageAdmissionService.StageAdmission(11L, "prep", RuleEvaluation.Outcome.MATCHED, null, true));
        });
        assertTrue(service.deliver(timer)); assertTrue(service.deliver(timer));
        verify(events).append(eq("Project"), eq("9"), any());
        var wrong = ProjectRuleTimer.create(7L, 9L, 51L, 61L, List.of(), ProjectRuleTimer.Purpose.ADMISSION, "time", due.minusSeconds(1), 62L);
        assertThrows(IllegalArgumentException.class, () -> service.deliver(wrong));
        verifyNoInteractions(stages, tasks, closure);
    }

    private ProjectNodeExecutionDO completedSource() {
        var source = new ProjectNodeExecutionDO(); source.setId(62L); source.setTenantId(7L); source.setProjectId(9L);
        source.setNodeKind("TASK"); source.setNodeInstanceId(12L); source.setNodeKey("task:survey"); source.setPlanVersionId(51L);
        source.setCurrentMarker(1); source.setStatus("DONE");
        source.setEndedAt(LocalDateTime.ofInstant(due.minusSeconds(1800), ZoneId.systemDefault())); return source;
    }

    private void relativeSourceRule(ProjectNodeExecutionDO source) {
        when(executions.selectCurrent(any())).thenReturn(List.of(round, source));
        when(executions.selectCurrentForUpdate(any())).thenReturn(List.of(round, source));
        snapshot.getStages().getFirst().setCompletionRuleKey(null); snapshot.getStages().getFirst().setExitRuleKey(null);
        var sourceDefinition = new TemplateExecutionSnapshot.TaskContract(); sourceDefinition.setNodeKey("task:survey");
        snapshot.setTasks(List.of(sourceDefinition));
        snapshot.setRulePrograms(Map.of("time", new ProjectRuleCompiler().compile(JsonUtils.parseTree("""
                {"predicate":"WAIT_ELAPSED","parameters":{"anchor":"NODE_COMPLETED","duration":"PT30M","sourceNodeKey":"task:survey"}}
                """)))); saveSnapshot();
    }

    private List<ProjectRuleTimer> capturedTimers(int count) {
        var captor = ArgumentCaptor.forClass(cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent.class);
        verify(events, times(count)).appendAt(eq("ProjectPlan"), eq("51"), captor.capture(), any());
        return captor.getAllValues().stream().map(event -> JsonUtils.parseObject(event.eventPayload(), ProjectRuleTimer.class)).toList();
    }
}
