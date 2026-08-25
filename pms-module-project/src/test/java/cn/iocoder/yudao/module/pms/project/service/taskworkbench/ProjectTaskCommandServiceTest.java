package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectStageInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateMachineRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskDependencyDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMilestoneInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskDependencyMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.domain.projectmanual.TaskExecutionContractFactory;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.AddDependencyCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.CreateTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.MoveTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.UpdateTaskCommand;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskCommandServiceTest {

    private static final String DIGEST = "a".repeat(64);
    private static final TaskWorkbenchActor ACTOR = new TaskWorkbenchActor(0L, 9L, "trace-1");

    @Mock ProjectTaskRuntimeMapper taskMapper;
    @Mock ProjectTaskInstanceMapper taskInstanceMapper;
    @Mock ProjectTaskDependencyMapper dependencyMapper;
    @Mock ProjectTaskExecutionContractMapper contractMapper;
    @Mock TaskStateMachineMapper stateMachineMapper;
    @Mock ProjectStageInstanceMapper stageMapper;
    @Mock ProjectMilestoneInstanceMapper milestoneMapper;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock ProjectTreeVersionMapper treeVersionMapper;
    @Mock ProjectTreeScopeService treeScopeService;
    @Mock PlatformCommandExecutionApi commandExecutionApi;

    private ProjectTaskCommandService service;
    private ProjectMasterDO project;

    @BeforeEach
    void setUp() {
        service = new ProjectTaskCommandService(taskMapper, taskInstanceMapper, dependencyMapper, contractMapper,
                new TaskExecutionContractFactory(), stateMachineMapper, stageMapper, milestoneMapper, memberMapper,
                treeVersionMapper, treeScopeService, commandExecutionApi);
        project = new ProjectMasterDO();
        project.setId(100L);
        project.setRootId(100L);
        project.setTenantId(0L);
        project.setLifecycleStatus("ACTIVE");
        project.setTaskTreeVersion(3L);
        ProjectMemberAssignmentDO manager = new ProjectMemberAssignmentDO();
        manager.setProjectId(100L);
        manager.setUserId(9L);
        manager.setMemberRole("PROJECT_MANAGER");
        manager.setStatus("ACTIVE");
        lenient().when(memberMapper.selectActiveByUser(any())).thenReturn(List.of(manager));
        ProjectTreeVersionDO treeVersion = new ProjectTreeVersionDO();
        treeVersion.setTreeVersion(1L);
        lenient().when(treeVersionMapper.selectLatestActive(100L)).thenReturn(treeVersion);
        delegatePlatformOperation();
    }

    @Test
    void createsTaskWithNativeContractAndTreeVersion() {
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        ProjectStageInstanceDO stage = new ProjectStageInstanceDO();
        stage.setStageCode("S1");
        when(stageMapper.selectByProjectIdAndStageCode(100L, "S1")).thenReturn(stage);
        TaskStateMachineRevisionDO revision = new TaskStateMachineRevisionDO();
        revision.setId(81L);
        when(stateMachineMapper.selectCurrentPublished(any())).thenReturn(revision);
        when(taskMapper.insert(any(ProjectTaskInstanceDO.class))).thenReturn(1);
        when(taskMapper.insertNewTaskPaths(any())).thenReturn(1);
        when(contractMapper.insert(any(ProjectTaskExecutionContractDO.class))).thenReturn(1);
        when(taskMapper.incrementTaskTreeVersion(any())).thenReturn(1);

        TaskCommandResult result = service.create(new CreateTaskCommand(100L, "T-1", "任务1", "S1",
                null, null, null, null, 1, 0, null, "key-1", DIGEST), ACTOR);

        assertEquals(0, result.taskVersion());
        assertEquals(4L, result.taskTreeVersion());
        verify(contractMapper).insert(any(ProjectTaskExecutionContractDO.class));
        verify(taskMapper).insertNewTaskPaths(any());
    }

    @Test
    void rejectsUpdateWhenTaskCasLoses() {
        ProjectTaskInstanceDO task = task(11L, 2, "IN_PROGRESS");
        when(taskMapper.selectTask(any())).thenReturn(task);
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(taskMapper.updateBasicIfMatch(any())).thenReturn(0);

        ServiceException error = assertThrows(ServiceException.class, () -> service.update(
                new UpdateTaskCommand(11L, 2, "更新", null, null, null, 1, 0, null), ACTOR));

        assertEquals(PROJECT_TASK_VERSION_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void rejectsMoveCycleWithoutWrites() {
        ProjectTaskInstanceDO source = task(11L, 2, "IN_PROGRESS");
        ProjectTaskInstanceDO target = task(12L, 1, "IN_PROGRESS");
        when(taskMapper.selectTask(any())).thenReturn(source);
        when(taskMapper.selectMoveLocks(any())).thenReturn(new ProjectTaskRuntimeMapper.ProjectTaskMoveLocks(
                project, source, target, List.of(target), true));

        ServiceException error = assertThrows(ServiceException.class, () -> service.move(
                new MoveTaskCommand(11L, 2, 12L, 3L, "move", "key-3", DIGEST), ACTOR));

        assertEquals(PROJECT_TASK_COMMAND_INVALID.getCode(), error.getCode());
        verify(taskMapper, never()).updateStructureIfMatch(any());
    }

    @Test
    void rejectsDependencyCycleWithoutInsert() {
        ProjectTaskInstanceDO successor = task(11L, 2, "IN_PROGRESS");
        ProjectTaskInstanceDO predecessor = task(12L, 1, "IN_PROGRESS");
        when(taskMapper.selectTask(any())).thenReturn(successor, predecessor);
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(dependencyMapper.existsDependencyPath(any())).thenReturn(true);

        ServiceException error = assertThrows(ServiceException.class, () -> service.addDependency(
                new AddDependencyCommand(11L, 2, 12L, "FINISH_TO_START", "key-4", DIGEST), ACTOR));

        assertEquals(PROJECT_TASK_COMMAND_INVALID.getCode(), error.getCode());
        verify(dependencyMapper, never()).insert(any(ProjectTaskDependencyDO.class));
    }

    @Test
    void mapsPlatformIdempotencyConflict() {
        when(commandExecutionApi.execute(any(), anyString(), eq(TaskCommandResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(
                new CreateTaskCommand(100L, "T-1", "任务1", "S1", null, null,
                        null, null, null, null, null, "key-5", DIGEST), ACTOR));

        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), error.getCode());
    }

    @Test
    void returnsCompletedReplayWithoutExecutingWrites() {
        TaskCommandResult completed = new TaskCommandResult(11L, 2, 3L, "IN_PROGRESS", "NEW");
        when(commandExecutionApi.execute(any(), anyString(), eq(TaskCommandResult.class), any(), any()))
                .thenReturn(new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, completed));

        TaskCommandResult result = service.create(new CreateTaskCommand(100L, "T-1", "任务1", "S1",
                null, null, null, null, null, null, null, "key-6", DIGEST), ACTOR);

        assertEquals("REPLAY_COMPLETED", result.replayDecision());
        verify(taskMapper, never()).insert(any(ProjectTaskInstanceDO.class));
    }

    @Test
    void rejectsTerminalTaskUpdateWithoutWrites() {
        when(taskMapper.selectTask(any())).thenReturn(task(11L, 2, "DONE"));
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);

        ServiceException error = assertThrows(ServiceException.class, () -> service.update(
                new UpdateTaskCommand(11L, 2, "更新", null, null, null, null, null, null), ACTOR));

        assertEquals(PROJECT_TASK_COMMAND_INVALID.getCode(), error.getCode());
        verify(taskMapper, never()).updateBasicIfMatch(any());
    }

    @Test
    void rejectsCrossProjectDependencyWithoutWrites() {
        ProjectTaskInstanceDO successor = task(11L, 2, "IN_PROGRESS");
        ProjectTaskInstanceDO predecessor = task(12L, 1, "IN_PROGRESS");
        predecessor.setProjectId(200L);
        when(taskMapper.selectTask(any())).thenReturn(successor, predecessor);
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);

        ServiceException error = assertThrows(ServiceException.class, () -> service.addDependency(
                new AddDependencyCommand(11L, 2, 12L, "FINISH_TO_START", "key-7", DIGEST), ACTOR));

        assertEquals(PROJECT_TASK_COMMAND_INVALID.getCode(), error.getCode());
        verify(dependencyMapper, never()).insert(any(ProjectTaskDependencyDO.class));
        verify(taskMapper, never()).incrementTaskVersionIfMatch(any(), any(), any(), any());
    }

    @Test
    void rejectsNonManagerWithoutWrites() {
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(memberMapper.selectActiveByUser(any())).thenReturn(List.of());

        ServiceException error = assertThrows(ServiceException.class, () -> service.create(
                new CreateTaskCommand(100L, "T-1", "任务1", "S1", null, null,
                        null, null, null, null, null, "key-8", DIGEST), ACTOR));

        assertEquals(PROJECT_TASK_SCOPE_FORBIDDEN.getCode(), error.getCode());
        verify(taskMapper, never()).insert(any(ProjectTaskInstanceDO.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void delegatePlatformOperation() {
        lenient().when(commandExecutionApi.execute(any(), anyString(), eq(TaskCommandResult.class), any(), any()))
                .thenAnswer(invocation -> {
                    Supplier operation = invocation.getArgument(3);
                    Object result = operation.get();
                    Function facts = invocation.getArgument(4);
                    facts.apply(result);
                    return new PlatformCommandExecutionApi.ExecutionResult<>(
                            PlatformCommandExecutionApi.Decision.NEW, result);
                });
    }

    private ProjectTaskInstanceDO task(Long id, int version, String status) {
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
        task.setId(id);
        task.setTenantId(0L);
        task.setProjectId(100L);
        task.setRootTaskId(11L);
        task.setTreeDepth(id.equals(11L) ? 0 : 1);
        task.setVersion(version);
        task.setStatus(status);
        return task;
    }
}
