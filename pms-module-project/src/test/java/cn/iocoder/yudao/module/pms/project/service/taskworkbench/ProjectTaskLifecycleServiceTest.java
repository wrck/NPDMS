package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectGateInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskCompletionEvaluationMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskLifecycleStateUpdate;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.TaskActionCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskLifecycleServiceTest {

    @Mock ProjectTaskRuntimeMapper taskMapper;
    @Mock ProjectTaskExecutionContractMapper contractMapper;
    @Mock ProjectTaskAssignmentMapper assignmentMapper;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock ProjectTaskCompletionEvaluationMapper evaluationMapper;
    @Mock ProjectGateInstanceMapper gateMapper;
    @Mock TaskStateMachineMapper stateMachineMapper;
    @Mock TaskNativeBindingHostProvider nativeProvider;
    @Mock PlatformCommandExecutionApi commandExecutionApi;
    @Mock OperationAuditApi operationAuditApi;

    private ProjectTaskLifecycleService service;
    private PlatformCommandExecutionApi.SuccessFacts successFacts;

    @BeforeEach
    void setUp() {
        service = new ProjectTaskLifecycleService(taskMapper, contractMapper, assignmentMapper, memberMapper,
                evaluationMapper, gateMapper,
                stateMachineMapper, nativeProvider, commandExecutionApi, operationAuditApi);
    }

    @Test
    void startUsesFrozenTransitionAndInitializesActualStartTime() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("start", 3, null, null), actor());

        assertEquals("IN_PROGRESS", result.status());
        ArgumentCaptor<TaskLifecycleStateUpdate> update = ArgumentCaptor.forClass(TaskLifecycleStateUpdate.class);
        verify(taskMapper).updateLifecycleIfMatch(update.capture());
        assertTrue(update.getValue().initializeActualStartTime());
        assertFalse(update.getValue().setActualEndTime());
        assertEquals("PROJECT_TASK_START", successFacts.operationCode());
    }

    @Test
    void submitFreezesProgressAtNinetyNine() {
        allowAction("IN_PROGRESS", "SUBMIT", "PENDING_ACCEPT");
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("submit", 3, null, null), actor());

        assertEquals("PENDING_ACCEPT", result.status());
        ArgumentCaptor<TaskLifecycleStateUpdate> update = ArgumentCaptor.forClass(TaskLifecycleStateUpdate.class);
        verify(taskMapper).updateLifecycleIfMatch(update.capture());
        assertEquals(99, update.getValue().progress());
    }

    @Test
    void cancelWritesActualEndAndPreservesProgress() {
        allowAction("IN_PROGRESS", "CANCEL", "CLOSED");
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("cancel", 3, null, null), actor());

        assertEquals("CLOSED", result.status());
        ArgumentCaptor<TaskLifecycleStateUpdate> update = ArgumentCaptor.forClass(TaskLifecycleStateUpdate.class);
        verify(taskMapper).updateLifecycleIfMatch(update.capture());
        assertTrue(update.getValue().setActualEndTime());
        assertEquals(null, update.getValue().progress());
    }

    @Test
    void completeAppendsSatisfiedEvaluationAdvancesDoneAndPublishesEvent() {
        allowAction("PENDING_ACCEPT", "COMPLETE", "DONE");
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);
        when(taskMapper.updateLifecycleIfMatch(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("complete", 3, 91L, 2), actor());

        assertEquals("DONE", result.status());
        verify(evaluationMapper).insertEvaluation(any());
        assertEquals("TaskCompleted", successFacts.eventType());
        assertTrue(successFacts.eventPayload().contains("completionEvaluationId"));
    }

    @Test
    void unmetDescendantPersistsEvaluationWithoutAdvancingTask() {
        allowAction("PENDING_ACCEPT", "COMPLETE", "DONE");
        when(taskMapper.countNonTerminalDescendants(any())).thenReturn(1L);
        when(evaluationMapper.insertEvaluation(any())).thenReturn(1);

        TaskCommandResult result = service.act(command("complete", 3, 91L, 2), actor());

        assertEquals("PENDING_ACCEPT", result.status());
        assertEquals(3, result.taskVersion());
        verify(evaluationMapper).insertEvaluation(any());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
        assertEquals(null, successFacts.eventType());
        assertTrue(successFacts.detailSnapshot().contains("NON_TERMINAL_DESCENDANT"));
    }

    @Test
    void startRejectsWhenLockedCurrentAssignmentNoLongerBelongsToActor() {
        allowAction("PENDING_START", "START", "IN_PROGRESS");
        ProjectTaskAssignmentDO reassigned = new ProjectTaskAssignmentDO();
        reassigned.setAssigneeUserId(10L);
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(reassigned);

        assertThrows(RuntimeException.class, () -> service.act(command("start", 3, null, null), actor()));

        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @Test
    void completeRejectsWhenLockedCurrentProjectManagerMembershipIsGone() {
        allowAction("PENDING_ACCEPT", "COMPLETE", "DONE");
        when(memberMapper.selectActiveByUserForUpdate(any())).thenReturn(java.util.List.of());

        assertThrows(RuntimeException.class, () -> service.act(command("complete", 3, 91L, 2), actor()));

        verify(evaluationMapper, never()).insertEvaluation(any());
        verify(taskMapper, never()).updateLifecycleIfMatch(any());
    }

    @SuppressWarnings("unchecked")
    private void allowAction(String status, String action, String target) {
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
        task.setId(11L);
        task.setProjectId(100L);
        task.setTenantId(0L);
        task.setName("任务");
        task.setStageCode("S1");
        task.setStatus(status);
        task.setVersion(3);
        task.setStateMachineRevisionId(81L);
        when(taskMapper.selectTask(any())).thenReturn(task);
        when(taskMapper.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(100L);
        project.setTenantId(0L);
        project.setLifecycleStatus("ACTIVE");
        project.setTaskTreeVersion(4L);
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        TaskStateTransitionDO transition = new TaskStateTransitionDO();
        transition.setFromStatusCode(status);
        transition.setActionCode(action);
        transition.setToStatusCode(target);
        when(stateMachineMapper.requireTransition(any())).thenReturn(transition);
        when(nativeProvider.inspect(any())).thenReturn(new TaskBindingInspection(
                "TASK_NATIVE", Set.of(action), "3:2:1", null));
        ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
        contract.setId(91L);
        contract.setTenantId(0L);
        contract.setProjectTaskId(11L);
        contract.setWorkBindingTypeCode("TASK_NATIVE");
        contract.setCompletionRuleSnapshot("{\"requiredStatus\":\"DONE\"}");
        contract.setContractVersion(2);
        when(contractMapper.selectCurrentByTaskId(11L)).thenReturn(contract);
        if (Set.of("START", "SUBMIT").contains(action)) {
            ProjectTaskAssignmentDO assignment = new ProjectTaskAssignmentDO();
            assignment.setAssigneeUserId(9L);
            when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(assignment);
        } else {
            ProjectMemberAssignmentDO member = new ProjectMemberAssignmentDO();
            member.setMemberRole("PROJECT_MANAGER");
            when(memberMapper.selectActiveByUserForUpdate(any())).thenReturn(java.util.List.of(member));
        }
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<TaskCommandResult> operation = invocation.getArgument(3);
            Function<TaskCommandResult, PlatformCommandExecutionApi.SuccessFacts> factsFactory =
                    invocation.getArgument(4);
            TaskCommandResult result = operation.get();
            successFacts = factsFactory.apply(result);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });
    }

    private TaskActionCommand command(String action, int version, Long contractId, Integer contractVersion) {
        return new TaskActionCommand(11L, version, action, action.equals("cancel") ? "取消" : null,
                contractId, contractVersion, null, null, "key-" + action, "a".repeat(64));
    }

    private TaskWorkbenchActor actor() {
        return new TaskWorkbenchActor(0L, 9L, "corr-1");
    }
}
