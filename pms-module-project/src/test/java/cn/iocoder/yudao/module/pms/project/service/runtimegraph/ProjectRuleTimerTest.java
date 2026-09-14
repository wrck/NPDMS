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
    final ProjectRuleTimerDelivery service = new ProjectRuleTimerDelivery(projects, plans, executions, contracts, admission, stages, tasks, closure, events, audit);
    final Instant due = Instant.now().minusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    ProjectMasterDO project;
    ProjectNodeExecutionDO round;
    TemplateExecutionSnapshot snapshot;
    ProjectPlanVersionDO plan;

    @BeforeEach void setup() {
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
                purpose == ProjectRuleTimer.Purpose.CLOSURE ? List.of(61L) : List.of(), purpose, "time", due);
    }

    @Test void schedulesFrozenOneShotEventsForEverySlotAndOnlyNewReworkRounds() {
        var scheduler = new ProjectRuleTimerScheduler(executions, events);
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
        var early = ProjectRuleTimer.create(7L,9L,51L,61L,List.of(),ProjectRuleTimer.Purpose.ADMISSION,"time",Instant.now().plusSeconds(60));
        assertFalse(service.deliver(early));
        TenantContextHolder.setTenantId(8L);
        assertThrows(IllegalArgumentException.class, () -> service.deliver(timer(ProjectRuleTimer.Purpose.ADMISSION)));
        TenantContextHolder.setTenantId(7L);
        snapshot.getStages().getFirst().setAdmissionRuleKey("other"); saveSnapshot();
        assertThrows(IllegalArgumentException.class, () -> service.deliver(timer(ProjectRuleTimer.Purpose.ADMISSION)));
        verifyNoInteractions(events, tasks, stages, admission, closure);
    }

    @Test void taskAdmissionPreservesUnknownAndNeverStartsBusinessWork() {
        round.setNodeKind("TASK");
        var node = new TemplateExecutionSnapshot.TaskContract(); node.setNodeKey("prep"); node.setAdmissionRuleKey("time");
        snapshot.setTasks(List.of(node)); snapshot.setStages(List.of()); saveSnapshot();
        var task = new ProjectTaskInstanceDO(); task.setId(11L);
        var contract = new ProjectTaskExecutionContractDO(); contract.setId(21L);
        when(projects.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(admission.taskAdmissionFact(project,task,contract)).thenReturn(RuleFact.unknown("OWNER_UNAVAILABLE"));
        assertFalse(service.deliver(timer(ProjectRuleTimer.Purpose.ADMISSION)));
        when(admission.taskAdmissionFact(project,task,contract)).thenReturn(RuleFact.known(false));
        assertTrue(service.deliver(timer(ProjectRuleTimer.Purpose.ADMISSION)));
        verify(executions, never()).activateIfPending(any());
        when(admission.taskAdmissionFact(project,task,contract)).thenReturn(RuleFact.known(true));
        when(executions.activateIfPending(any())).thenReturn(1);
        assertTrue(service.deliver(timer(ProjectRuleTimer.Purpose.ADMISSION)));
        verify(executions).activateIfPending(argThat(write -> write.planVersionId()==51L && write.nodeInstanceId()==11L && write.nodeKind().equals("TASK")));
        verifyNoInteractions(tasks, stages, closure);
    }
}
