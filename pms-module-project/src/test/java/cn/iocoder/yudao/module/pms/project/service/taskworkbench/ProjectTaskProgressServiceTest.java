package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectprogress.ProjectProgressFactDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskworkbench.ProjectTaskAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectprogress.ProjectProgressFactMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ApplicableLeafTaskProgress;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.UpdateTaskProgressCommand;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskProgressServiceTest {

    @Mock ProjectTaskRuntimeMapper taskMapper;
    @Mock ProjectTaskAssignmentMapper assignmentMapper;
    @Mock ProjectProgressFactMapper factMapper;
    @Mock ProjectTreeVersionMapper treeVersionMapper;
    @Mock ProjectTreeScopeService treeScopeService;
    @Mock PermissionApi permissionApi;
    @Mock OperationAuditApi operationAuditApi;

    private ProjectTaskProgressService service;

    @BeforeEach
    void setUp() {
        service = new ProjectTaskProgressService(taskMapper, assignmentMapper, factMapper, treeVersionMapper,
                treeScopeService, permissionApi, operationAuditApi);
    }

    @Test
    void recomputeUsesLeafWeightsAndStatusSemantics() {
        ProjectMasterDO project = project(7L);
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(taskMapper.selectApplicableLeaves(any())).thenReturn(List.of(
                leaf(1L, "IN_PROGRESS", "50", "2"),
                leaf(2L, "PENDING_ACCEPT", "40", null),
                leaf(3L, "DONE", "70", "0"),
                leaf(4L, "PENDING_ASSIGN", "80", "-1")));
        when(taskMapper.incrementTaskProgressVersion(any())).thenReturn(1);
        when(factMapper.insert(any(ProjectProgressFactDO.class))).thenReturn(1);

        var fact = service.recompute(0L, 100L, 7L, LocalDateTime.of(2026, 8, 26, 1, 0)).orElseThrow();

        assertEquals(new BigDecimal("59.8000"), fact.progress());
        assertEquals(8L, fact.factVersion());
        assertTrue(fact.sourceWatermark().contains("\"participantCount\":4"));
    }

    @Test
    void recomputeWithNoApplicableLeafDoesNotCreateZeroFact() {
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project(2L));
        when(taskMapper.selectApplicableLeaves(any())).thenReturn(List.of());

        assertTrue(service.recompute(0L, 100L, 2L, LocalDateTime.now()).isEmpty());
        verify(taskMapper, never()).incrementTaskProgressVersion(any());
        verify(factMapper, never()).insert(any(ProjectProgressFactDO.class));
    }

    @Test
    void allMissingWeightsAreEqualAndClosedPreservesStoredProgress() {
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project(0L));
        when(taskMapper.selectApplicableLeaves(any())).thenReturn(List.of(
                leaf(1L, "PENDING_START", "80", null),
                leaf(2L, "CLOSED", "40", null)));
        when(taskMapper.incrementTaskProgressVersion(any())).thenReturn(1);
        when(factMapper.insert(any(ProjectProgressFactDO.class))).thenReturn(1);

        var fact = service.recompute(0L, 100L, 0L, LocalDateTime.now()).orElseThrow();

        assertEquals(new BigDecimal("20.0000"), fact.progress());
    }

    @Test
    void unknownLeafStatusFailsClosedWithoutVersionOrFact() {
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project(0L));
        when(taskMapper.selectApplicableLeaves(any())).thenReturn(
                List.of(leaf(1L, "UNKNOWN", "50", "1")));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.recompute(0L, 100L, 0L, LocalDateTime.now()));

        assertEquals(PROJECT_TASK_COMMAND_INVALID.getCode(), error.getCode());
        verify(taskMapper, never()).incrementTaskProgressVersion(any());
        verify(factMapper, never()).insert(any(ProjectProgressFactDO.class));
    }

    @Test
    void progressOnlyUpdateRequiresCurrentAssigneeAndAppendsFact() {
        ProjectMasterDO project = project(4L);
        ProjectTaskInstanceDO task = task(11L, "IN_PROGRESS", 3, "20");
        ProjectTaskAssignmentDO assignment = new ProjectTaskAssignmentDO();
        assignment.setAssigneeUserId(9L);
        ProjectTreeVersionDO treeVersion = new ProjectTreeVersionDO();
        treeVersion.setTreeVersion(6L);
        when(taskMapper.selectTask(any())).thenReturn(task);
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(taskMapper.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:execute")).thenReturn(true);
        when(treeVersionMapper.selectLatestActive(100L)).thenReturn(treeVersion);
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(assignment);
        when(taskMapper.selectApplicableLeaves(any())).thenReturn(List.of(leaf(11L, "IN_PROGRESS", "60", "2")));
        when(taskMapper.updateProgressIfMatch(any())).thenReturn(1);
        when(taskMapper.incrementTaskProgressVersion(any())).thenReturn(1);
        when(factMapper.insert(any(ProjectProgressFactDO.class))).thenReturn(1);

        var result = service.updateProgress(new UpdateTaskProgressCommand(11L, 3, 60),
                new TaskWorkbenchActor(0L, 9L, "trace-progress"));

        assertEquals(4, result.taskVersion());
        verify(treeScopeService).assertFullAccess(new ProjectScopeQuery(
                0L, 9L, 100L, ProjectScopeApi.ACTION_EDIT, 6L));
        verify(operationAuditApi).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsDirectProgressUpdateForNonLeafTask() {
        ProjectMasterDO project = project(4L);
        ProjectTaskInstanceDO task = task(11L, "IN_PROGRESS", 3, "20");
        ProjectTaskAssignmentDO assignment = new ProjectTaskAssignmentDO();
        assignment.setAssigneeUserId(9L);
        ProjectTreeVersionDO treeVersion = new ProjectTreeVersionDO();
        treeVersion.setTreeVersion(6L);
        when(taskMapper.selectTask(any())).thenReturn(task);
        when(taskMapper.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(taskMapper.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:execute")).thenReturn(true);
        when(treeVersionMapper.selectLatestActive(100L)).thenReturn(treeVersion);
        when(assignmentMapper.selectCurrentForUpdate(any())).thenReturn(assignment);
        when(taskMapper.selectApplicableLeaves(any())).thenReturn(List.of(leaf(12L, "IN_PROGRESS", "40", "1")));

        ServiceException error = assertThrows(ServiceException.class, () -> service.updateProgress(
                new UpdateTaskProgressCommand(11L, 3, 60), new TaskWorkbenchActor(0L, 9L, "trace-progress")));

        assertEquals(PROJECT_TASK_COMMAND_INVALID.getCode(), error.getCode());
        verify(taskMapper, never()).updateProgressIfMatch(any());
    }

    private ProjectMasterDO project(long taskProgressVersion) {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(100L);
        project.setRootId(100L);
        project.setTenantId(0L);
        project.setLifecycleStatus("ACTIVE");
        project.setTaskTreeVersion(5L);
        project.setTaskProgressVersion(taskProgressVersion);
        return project;
    }

    private ProjectTaskInstanceDO task(long id, String status, int version, String progress) {
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
        task.setId(id);
        task.setProjectId(100L);
        task.setStatus(status);
        task.setVersion(version);
        task.setProgress(new BigDecimal(progress));
        return task;
    }

    private ApplicableLeafTaskProgress leaf(long id, String status, String progress, String hours) {
        ApplicableLeafTaskProgress leaf = new ApplicableLeafTaskProgress();
        leaf.setTaskId(id);
        leaf.setStatus(status);
        leaf.setProgress(new BigDecimal(progress));
        if (hours != null) leaf.setEstimatedHours(new BigDecimal(hours));
        return leaf;
    }
}
