package cn.iocoder.yudao.module.pms.project.service.projectplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.runtimegraph.ProjectRuntimeGraphMapper;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleOutboxDeliveryJob;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleReevaluation;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectRuleTimerDelivery;
import cn.iocoder.yudao.module.pms.project.service.runtimegraph.ProjectStageAdmissionService;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectTaskBusinessAssociationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real coordinator and Outbox consumer; mocks isolate persistence and formal node commands. */
class ProjectRuntimeClosureDeliveryTest {
    private final ProjectMasterMapper projects = mock(ProjectMasterMapper.class);
    private final ProjectRuntimeGraphMapper graph = mock(ProjectRuntimeGraphMapper.class);
    private final ProjectStageAdmissionService admission = mock(ProjectStageAdmissionService.class);
    private final ProjectStageCompletionService completion = mock(ProjectStageCompletionService.class);
    private final ProjectRuleClosureService closure = mock(ProjectRuleClosureService.class);
    private final ProjectBusinessTaskCompletionService tasks = mock(ProjectBusinessTaskCompletionService.class);
    private final ProjectGateRuleService gates = mock(ProjectGateRuleService.class);
    private final ProjectTaskBusinessAssociationService associations = mock(ProjectTaskBusinessAssociationService.class);
    private final PlatformOutboxDeliveryApi outbox = mock(PlatformOutboxDeliveryApi.class);
    private final ProjectRuntimeCoordinator coordinator = new ProjectRuntimeCoordinator(
            admission, completion, closure, tasks, gates, graph, associations, projects);
    private final ProjectRuleOutboxDeliveryJob job = new ProjectRuleOutboxDeliveryJob(
            outbox, coordinator, mock(ProjectRuleTimerDelivery.class));
    private final ProjectMasterDO project = new ProjectMasterDO();
    private final cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent event =
            new ProjectRuleReevaluation(7L, 9L, 11L, "closure-event").event();

    @BeforeEach void setup() {
        TenantContextHolder.setTenantId(7L);
        project.setId(9L); project.setTenantId(7L); project.setLifecycleStatus("ACTIVE");
        when(projects.selectById(9L)).thenReturn(project);
        when(outbox.claimDue(any())).thenReturn(List.of(new PlatformOutboxMessageDTO(
                event.eventId(), event.eventType(), event.eventPayload(), 3, 7L, LocalDateTime.now())));
    }

    @AfterEach void clear() { TenantContextHolder.clear(); }

    @ParameterizedTest
    @ValueSource(strings = {"NORMAL_CLOSED", "EXCEPTION_CLOSED"})
    void closedProjectAcknowledgesDelayedAndRepeatedEventsWithoutTouchingNodes(String status) {
        project.setLifecycleStatus(status);
        job.execute("");
        job.execute("");
        verify(outbox, times(2)).markDelivered(event.eventId(), 3);
        verify(outbox, never()).scheduleRetry(anyString(), anyInt(), any());
        verifyNoInteractions(graph, admission, completion, closure, tasks, gates, associations);
    }

    @Test void missingProjectRetriesRatherThanAcknowledgingAnUnprovenClosure() {
        when(projects.selectById(9L)).thenReturn(null);
        assertRetryWithoutNodeExecution();
    }

    @Test void anotherTenantsClosedProjectCannotBeUsedToAcknowledgeTheEvent() {
        project.setTenantId(8L); project.setLifecycleStatus("NORMAL_CLOSED");
        assertRetryWithoutNodeExecution();
    }

    @Test void unrecognizedLifecycleRetriesRatherThanBeingTreatedAsClosed() {
        project.setLifecycleStatus(null);
        assertRetryWithoutNodeExecution();
    }

    @Test void unavailableProjectReadKeepsTheEventForRecovery() {
        when(projects.selectById(9L)).thenThrow(new IllegalStateException("database unavailable"));
        assertRetryWithoutNodeExecution();
    }

    @Test void formalClosureEndsRetriesForUnavailableOptionalBranches() {
        when(tasks.completeEligible(9L, "closure-event"))
                .thenReturn(new ProjectBusinessTaskCompletionService.Result(0, 0, true));
        when(closure.closeIfSatisfied(9L, 11L, "closure-event")).thenAnswer(call -> {
            project.setLifecycleStatus("NORMAL_CLOSED");
            return new ProjectRuleClosureService.Closure(true, false);
        });
        job.execute("");
        job.execute("");
        verify(outbox, times(2)).markDelivered(event.eventId(), 3);
        verify(outbox, never()).scheduleRetry(anyString(), anyInt(), any());
        verify(closure).closeIfSatisfied(9L, 11L, "closure-event");
        verify(tasks).completeEligible(9L, "closure-event");
    }

    @Test void anUnclosedProjectKeepsUnknownBranchesOnTheExistingRetryPath() {
        when(tasks.completeEligible(9L, "closure-event"))
                .thenReturn(new ProjectBusinessTaskCompletionService.Result(0, 0, true));
        when(closure.closeIfSatisfied(9L, 11L, "closure-event"))
                .thenReturn(new ProjectRuleClosureService.Closure(false, false));
        job.execute("");
        verify(outbox).scheduleRetry(eq(event.eventId()), eq(3), any());
        verify(outbox, never()).markDelivered(anyString(), anyInt());
        assertEquals("ACTIVE", project.getLifecycleStatus());
    }

    private void assertRetryWithoutNodeExecution() {
        job.execute("");
        verify(outbox).scheduleRetry(eq(event.eventId()), eq(3), any());
        verify(outbox, never()).markDelivered(anyString(), anyInt());
        verifyNoInteractions(graph, admission, completion, closure, tasks, gates, associations);
    }
}
