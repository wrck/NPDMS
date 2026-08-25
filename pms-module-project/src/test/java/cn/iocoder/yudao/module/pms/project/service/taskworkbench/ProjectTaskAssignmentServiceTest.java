package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskAssigneeCandidateReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.TaskStateTransitionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.TaskStateMachineMapper;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.ProjectTaskCommands.AssignTaskCommand;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.permission.OrganizationScopeApi;
import cn.iocoder.yudao.module.system.api.permission.dto.OrganizationUserCandidateRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskAssignmentServiceTest {

    @Mock private ProjectTaskRuntimeMapper taskMapper;
    @Mock private ProjectTaskAssignmentMapper assignmentMapper;
    @Mock private TaskStateMachineMapper stateMachineMapper;
    @Mock private ProjectMasterMapper projectMapper;
    @Mock private ProjectMemberAssignmentMapper memberMapper;
    @Mock private ProjectTreeVersionMapper treeVersionMapper;
    @Mock private ProjectTreeScopeService treeScopeService;
    @Mock private CompanyApi companyApi;
    @Mock private DeptApi deptApi;
    @Mock private OrganizationScopeApi organizationScopeApi;
    @Mock private AdminUserApi adminUserApi;
    @Mock private PlatformCommandExecutionApi commandExecutionApi;
    @Mock private OperationAuditApi operationAuditApi;

    @InjectMocks private ProjectTaskAssignmentService service;
    private final AtomicReference<PlatformCommandExecutionApi.SuccessFacts> successFacts = new AtomicReference<>();

    @Test
    void candidateQueryReusesProjectOrganizationScopeAndStablePagination() {
        allowCandidateProject("PROJECT_MANAGER");
        DeptRespDTO department = new DeptRespDTO();
        department.setId(20L);
        department.setCode("DEP-01");
        when(deptApi.getDeptByCode("DEP-01")).thenReturn(department);
        OrganizationUserCandidateRespDTO candidate = new OrganizationUserCandidateRespDTO();
        candidate.setUserId(66L);
        candidate.setCompanyId(10L);
        candidate.setDepartmentId(20L);
        candidate.setDepartmentCode("DEP-01");
        when(organizationScopeApi.pageActiveUsers(any())).thenReturn(new PageResult<>(List.of(candidate), 1L));

        var result = service.getAssigneeCandidates(100L, request(), actor());

        assertEquals(1L, result.getTotal());
        assertEquals(66L, result.getList().getFirst().getUserId());
        verify(organizationScopeApi).pageActiveUsers(any());
    }

    @Test
    void engineerCannotQueryAssigneeCandidatesEvenWithManageScope() {
        allowCandidateProject("ENGINEER");

        assertThrows(ServiceException.class,
                () -> service.getAssigneeCandidates(100L, request(), actor()));

        verify(organizationScopeApi, never()).pageActiveUsers(any());
    }

    @Test
    void firstAssignmentCreatesIntervalAdvancesStatusAndPublishesOneFact() {
        allowAssignment("PROJECT_MANAGER");
        when(assignmentMapper.insertAssignment(any())).thenReturn(1);
        when(taskMapper.assignTaskIfMatch(any())).thenReturn(1);

        var result = service.assign(command(66L, "首次指派", "key-1"), actor());

        assertEquals("PENDING_START", result.status());
        assertEquals(1, result.taskVersion());
        assertEquals("TaskAssigned", successFacts.get().eventType());
        verify(assignmentMapper).insertAssignment(any());
        verify(taskMapper).assignTaskIfMatch(any());
        verify(commandExecutionApi).execute(any(), eq("a".repeat(64)), eq(
                cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult.class),
                any(), any());
    }

    @Test
    void transferClosesHistoricalIntervalAndKeepsCurrentStatus() {
        ProjectTaskAssignmentDO current = new ProjectTaskAssignmentDO();
        current.setId(900L);
        current.setAssigneeUserId(55L);
        current.setVersion(2);
        allowAssignment("SERVICE_MANAGER_L1");
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(current);
        when(taskMapper.selectTaskForAssignmentForUpdate(any())).thenAnswer(invocation -> {
            ProjectTaskInstanceDO task = task();
            task.setStatus("IN_PROGRESS");
            task.setVersion(4);
            return task;
        });
        allowKnownStatus("IN_PROGRESS");
        when(assignmentMapper.closeCurrentIfMatch(any())).thenReturn(1);
        when(assignmentMapper.insertAssignment(any())).thenReturn(1);
        when(taskMapper.assignTaskIfMatch(any())).thenReturn(1);

        var result = service.assign(new AssignTaskCommand(100L, 4, 66L, "跨区域转派", "key-2",
                "b".repeat(64)), actor());

        assertEquals("IN_PROGRESS", result.status());
        assertEquals(5, result.taskVersion());
        verify(assignmentMapper).closeCurrentIfMatch(any());
        verify(assignmentMapper).insertAssignment(any());
    }

    @Test
    void transferKeepsPublishedExtensionIntermediateStatus() {
        ProjectTaskAssignmentDO current = new ProjectTaskAssignmentDO();
        current.setId(901L);
        current.setAssigneeUserId(55L);
        current.setVersion(1);
        allowAssignment("PROJECT_MANAGER");
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(current);
        when(taskMapper.selectTaskForAssignmentForUpdate(any())).thenAnswer(invocation -> {
            ProjectTaskInstanceDO task = task();
            task.setStatus("WAITING_EXTERNAL_CONFIRMATION");
            task.setVersion(3);
            return task;
        });
        allowKnownStatus("WAITING_EXTERNAL_CONFIRMATION");
        when(assignmentMapper.closeCurrentIfMatch(any())).thenReturn(1);
        when(assignmentMapper.insertAssignment(any())).thenReturn(1);
        when(taskMapper.assignTaskIfMatch(any())).thenReturn(1);

        var result = service.assign(new AssignTaskCommand(100L, 3, 66L, "扩展状态转派", "key-ext",
                "c".repeat(64)), actor());

        assertEquals("WAITING_EXTERNAL_CONFIRMATION", result.status());
        assertEquals(4, result.taskVersion());
    }

    @Test
    void candidateOutsideProjectOrganizationIsRejectedWithoutBusinessWrites() {
        allowAssignment("PROJECT_MANAGER");
        when(organizationScopeApi.hasScope(66L, 10L, 20L)).thenReturn(false);

        assertThrows(ServiceException.class,
                () -> service.assign(command(66L, "无范围候选", "key-3"), actor()));

        verify(assignmentMapper, never()).insertAssignment(any());
        verify(taskMapper, never()).assignTaskIfMatch(any());
        verify(operationAuditApi).record(eq(1L), eq(7L), eq("corr-1"), eq("PROJECT_TASK_ASSIGN"),
                eq("ProjectTask"), eq("100"), eq("REJECTED"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void closedProjectIsRejectedAfterLockWithoutAssignmentWrites() {
        when(taskMapper.selectTask(any())).thenReturn(task());
        ProjectMasterDO project = project();
        project.setLifecycleStatus("NORMAL_CLOSED");
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult> action =
                    invocation.getArgument(3);
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, action.get());
        });

        assertThrows(ServiceException.class,
                () -> service.assign(command(66L, "关闭项目禁止指派", "key-closed"), actor()));

        verify(assignmentMapper, never()).insertAssignment(any());
        verify(taskMapper, never()).assignTaskIfMatch(any());
    }

    @Test
    void completedReplayReturnsOriginalResultWithoutRepeatingAssignment() {
        var original = new cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult(
                100L, 1, 4L, "PENDING_START", "NEW");
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED, original));

        var result = service.assign(command(66L, "重放", "key-replay"), actor());

        assertEquals("REPLAY_COMPLETED", result.replayDecision());
        verify(assignmentMapper, never()).insertAssignment(any());
    }

    @Test
    void sameKeyDifferentPayloadConflictIsRejectedWithoutAssignment() {
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenReturn(
                new PlatformCommandExecutionApi.ExecutionResult<>(
                        PlatformCommandExecutionApi.Decision.CONFLICT, null));

        assertThrows(ServiceException.class,
                () -> service.assign(command(66L, "异载荷", "key-conflict"), actor()));

        verify(assignmentMapper, never()).insertAssignment(any());
        verify(operationAuditApi).record(eq(1L), eq(7L), eq("corr-1"), eq("PROJECT_TASK_ASSIGN"),
                eq("ProjectTask"), eq("100"), eq("REJECTED"), any());
    }

    private void allowProject(String role) {
        ProjectTaskInstanceDO task = task();
        when(taskMapper.selectTask(any())).thenReturn(task);
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(3L);
        when(treeVersionMapper.selectLatestActive(1L)).thenReturn(version);
        ProjectMemberAssignmentDO assignment = new ProjectMemberAssignmentDO();
        assignment.setProjectId(1L);
        assignment.setUserId(7L);
        assignment.setMemberRole(role);
        when(memberMapper.selectActiveByUser(any())).thenReturn(List.of(assignment));
    }

    private void allowCandidateProject(String role) {
        allowProject(role);
        when(projectMapper.selectById(1L)).thenReturn(project());
    }

    @SuppressWarnings("unchecked")
    private void allowAssignment(String role) {
        allowProject(role);
        ProjectMasterDO project = project();
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(taskMapper.selectTaskForAssignmentForUpdate(any())).thenReturn(task());
        DeptRespDTO department = new DeptRespDTO();
        department.setId(20L);
        department.setCode("DEP-01");
        when(deptApi.getDeptByCode("DEP-01")).thenReturn(department);
        when(organizationScopeApi.hasScope(anyLong(), eq(10L), eq(20L))).thenReturn(true);
        when(commandExecutionApi.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult> action =
                    invocation.getArgument(3);
            Function<cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult,
                    PlatformCommandExecutionApi.SuccessFacts> facts = invocation.getArgument(4);
            var result = action.get();
            successFacts.set(facts.apply(result));
            return new PlatformCommandExecutionApi.ExecutionResult<>(
                    PlatformCommandExecutionApi.Decision.NEW, result);
        });
    }

    private ProjectMasterDO project() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(1L);
        project.setRootId(1L);
        project.setTenantId(1L);
        project.setLifecycleStatus("ACTIVE");
        project.setCompanyId(10L);
        project.setDepartmentId(20L);
        project.setDepartmentCode("DEP-01");
        project.setTaskTreeVersion(4L);
        return project;
    }

    private ProjectTaskInstanceDO task() {
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
        task.setId(100L);
        task.setProjectId(1L);
        task.setTenantId(1L);
        task.setStatus("PENDING_ASSIGN");
        task.setStateMachineRevisionId(88L);
        task.setVersion(0);
        return task;
    }

    private void allowKnownStatus(String status) {
        TaskStateTransitionDO transition = new TaskStateTransitionDO();
        transition.setFromStatusCode(status);
        transition.setToStatusCode("PENDING_ACCEPT");
        when(stateMachineMapper.selectTransitions(any())).thenReturn(List.of(transition));
    }

    private AssignTaskCommand command(Long assignee, String reason, String key) {
        return new AssignTaskCommand(100L, 0, assignee, reason, key, "a".repeat(64));
    }

    private ProjectTaskAssigneeCandidateReqVO request() {
        ProjectTaskAssigneeCandidateReqVO request = new ProjectTaskAssigneeCandidateReqVO();
        request.setPageNo(2);
        request.setPageSize(25);
        request.setKeyword("王");
        return request;
    }

    private TaskWorkbenchActor actor() {
        return new TaskWorkbenchActor(1L, 7L, "corr-1");
    }
}
