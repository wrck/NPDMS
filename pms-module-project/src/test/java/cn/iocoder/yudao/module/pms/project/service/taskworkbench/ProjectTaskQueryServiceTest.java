package cn.iocoder.yudao.module.pms.project.service.taskworkbench;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskTreeQueryReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMemberAssignmentDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskExecutionContractDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectTaskInstanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectStageInstanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.ProjectTaskTreeQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.TaskAncestorBatchQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectTaskQueryServiceTest {

    @Mock ProjectMasterMapper projectMapper;
    @Mock ProjectTreeVersionMapper projectTreeVersionMapper;
    @Mock ProjectTreeScopeService projectTreeScopeService;
    @Mock ProjectMemberAssignmentMapper memberMapper;
    @Mock ProjectStageInstanceMapper stageMapper;
    @Mock ProjectTaskRuntimeMapper taskMapper;
    @Mock ProjectTaskAssignmentMapper assignmentMapper;
    @Mock ProjectTaskExecutionContractMapper contractMapper;
    @Mock TaskBindingHostRegistry bindingRegistry;
    @Mock PermissionCommonApi permissionApi;

    private ProjectTaskQueryService service;

    @BeforeEach
    void setUp() {
        service = new ProjectTaskQueryService(projectMapper, projectTreeVersionMapper,
                projectTreeScopeService, memberMapper, stageMapper, taskMapper, assignmentMapper,
                contractMapper, bindingRegistry, permissionApi);
    }

    @Test
    void plannedWorkspaceUsesPlanPermissionAndEditScopeWithoutAnExtraManagerRequirement() {
        stubProjectScope(false);
        projectMapper.selectById(100L).setActivePlanVersionId(50L);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-plan:manage")).thenReturn(true);

        assertEquals(Set.of("MANAGE_PLAN"), service.getWorkspace(100L, actor()).getAllowedActions());
        verify(projectTreeScopeService).resolve(argThat(query -> ProjectScopeApi.ACTION_EDIT.equals(query.actionCode())
                && query.tenantId() == 0L && query.subjectUserId() == 9L && query.anchorProjectId() == 100L
                && query.expectedTreeVersion() == 7L));
        verify(permissionApi, never()).hasAnyPermissions(9L, "pms:project-task:create");
    }

    @Test
    void plannedWorkspaceRejectsMissingPermissionRestrictedEditScopeAndClosedProject() {
        stubProjectScope(false);
        var project = projectMapper.selectById(100L);
        project.setActivePlanVersionId(50L);
        assertTrue(service.getWorkspace(100L, actor()).getAllowedActions().isEmpty());

        when(permissionApi.hasAnyPermissions(9L, "pms:project-plan:manage")).thenReturn(true);
        when(projectTreeScopeService.resolve(any())).thenAnswer(invocation -> {
            ProjectScopeQuery query = invocation.getArgument(0);
            return new ProjectTreeScopeService.ProjectTreeScope(100L, 7L,
                    ProjectScopeApi.ACTION_EDIT.equals(query.actionCode()) ? Set.of() : Set.of(100L),
                    Set.of(), Set.of());
        });
        assertTrue(service.getWorkspace(100L, actor()).getAllowedActions().isEmpty());

        org.mockito.Mockito.doReturn(new ProjectTreeScopeService.ProjectTreeScope(
                100L, 7L, Set.of(100L), Set.of(), Set.of())).when(projectTreeScopeService).resolve(any());
        project.setLifecycleStatus("CLOSED_NORMAL");
        assertTrue(service.getWorkspace(100L, actor()).getAllowedActions().isEmpty());
    }

    @Test
    void plannedWorkbenchKeepsHandlingButDoesNotOfferDirectMove() {
        stubProjectScope(true);
        projectMapper.selectById(100L).setActivePlanVersionId(50L);
        when(taskMapper.selectTask(any())).thenReturn(task(11L, null, 0));
        var contract = new ProjectTaskExecutionContractDO();
        contract.setId(91L); contract.setTenantId(0L); contract.setProjectTaskId(11L);
        contract.setWorkBindingTypeCode("TASK_NATIVE"); contract.setContractVersion(1);
        when(contractMapper.selectCurrentByTaskId(11L)).thenReturn(contract);
        when(bindingRegistry.inspect(any(), any())).thenReturn(
                new TaskBindingInspection("TASK_NATIVE", Set.of("START"), "0:1:0", null));
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:update")).thenReturn(true);

        assertEquals(Set.of("START", "UPDATE"), service.getWorkbench(11L, actor()).getAllowedActions());
        verify(taskMapper).selectTask(any());
        verify(permissionApi, never()).hasAnyPermissions(9L, "pms:project-task:move");
    }

    @Test
    void maintenanceAccessReusesTaskGrantsWithoutLoadingOwnerCompletionFacts() {
        stubProjectScope(true);
        var task = task(11L, null, 0);
        when(taskMapper.selectTask(any())).thenReturn(task);
        var contract = new ProjectTaskExecutionContractDO();
        contract.setTenantId(0L);
        contract.setWorkBindingTypeCode("BUSINESS_OBJECT");
        when(contractMapper.selectCurrentByTaskId(11L)).thenReturn(contract);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:update")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:move")).thenReturn(false);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:assign")).thenReturn(true);
        var access = service.getMaintenanceAccess(11L, actor());
        assertTrue(access.canAssign()); assertTrue(access.canUpdate());
        assertEquals(task.getVersion(), access.taskVersion());
        task.setStatus("DONE");
        assertFalse(service.getMaintenanceAccess(11L, actor()).canUpdate());
        task.setStatus("PENDING_START");
        contract.setTenantId(1L);
        assertFalse(service.getMaintenanceAccess(11L, actor()).canAssign());
        verifyNoInteractions(bindingRegistry);
    }

    @Test
    void maintenancePreservesNativeProviderAssignmentGrantForNonProjectManagers() {
        stubProjectScope(false);
        when(taskMapper.selectTask(any())).thenReturn(task(11L, null, 0));
        when(taskMapper.selectFullTaskIds(any())).thenReturn(List.of(11L));
        var contract = new ProjectTaskExecutionContractDO();
        contract.setTenantId(0L);
        contract.setWorkBindingTypeCode("TASK_NATIVE");
        when(contractMapper.selectCurrentByTaskId(11L)).thenReturn(contract);
        when(bindingRegistry.inspect(any(), any())).thenReturn(
                new TaskBindingInspection("TASK_NATIVE", Set.of("ASSIGN"), "0:1:0", null));

        var access = service.getMaintenanceAccess(11L, actor());
        assertTrue(access.canAssign());
        assertFalse(access.canUpdate());
    }

    @Test
    void shouldSupportFiveStableModesWithinFullProjectScope() {
        stubProjectScope(true);
        when(taskMapper.selectTree(any())).thenAnswer(invocation -> {
            ProjectTaskTreeQuery query = invocation.getArgument(0);
            return List.of(task(query.targetTaskId() == null ? 11L : query.targetTaskId(), null, 0));
        });
        when(assignmentMapper.selectCurrent(any())).thenReturn(List.of());

        for (String mode : List.of("DIRECT_CHILDREN", "ALL_DESCENDANTS", "ANCESTOR_CHAIN",
                "BUSINESS_LEVEL", "LOCATE")) {
            ProjectTaskTreeQueryReqVO request = new ProjectTaskTreeQueryReqVO();
            request.setMode(mode);
            if (Set.of("ALL_DESCENDANTS", "ANCESTOR_CHAIN", "LOCATE").contains(mode)) request.setTaskId(11L);
            if ("BUSINESS_LEVEL".equals(mode)) request.setBusinessLevelCode("L1");
            assertEquals(1, service.getTasks(100L, request, actor()).getRows().size());
        }
    }

    @Test
    void shouldReturnOnlyStructuralFieldsForUnauthorizedAncestor() {
        stubProjectScope(false);
        when(taskMapper.selectFullTaskIds(any())).thenReturn(List.of(2L));
        when(taskMapper.selectTree(any())).thenReturn(List.of(task(1L, null, 0), task(2L, 1L, 1)));
        when(assignmentMapper.selectCurrent(any())).thenReturn(List.of());
        ProjectTaskTreeQueryReqVO request = new ProjectTaskTreeQueryReqVO();
        request.setMode("ANCESTOR_CHAIN");
        request.setTaskId(2L);

        var response = service.getTasks(100L, request, actor());

        assertTrue(response.getRows().getFirst().isPlaceholder());
        assertNull(response.getRows().getFirst().getName());
        assertNull(response.getRows().getFirst().getAssigneeUserId());
        assertEquals(1L, response.getRows().getFirst().getTaskId());
    }

    @Test
    void shouldBatchLocateAncestorsOnceAndDeduplicateSharedPath() {
        stubProjectScope(true);
        when(taskMapper.selectTree(any())).thenReturn(List.of(
                task(3L, 2L, 2), task(4L, 2L, 2)));
        when(taskMapper.selectAncestors(any())).thenReturn(List.of(
                task(1L, null, 0), task(2L, 1L, 1)));
        when(assignmentMapper.selectCurrent(any())).thenReturn(List.of());
        ProjectTaskTreeQueryReqVO request = new ProjectTaskTreeQueryReqVO();
        request.setMode("LOCATE");
        request.setKeyword("Task");

        var response = service.getTasks(100L, request, actor());

        assertEquals(List.of(1L, 2L, 3L, 4L),
                response.getRows().stream().map(item -> item.getTaskId()).toList());
        verify(taskMapper).selectAncestors(argThat((TaskAncestorBatchQuery query) ->
                query.descendantTaskIds().equals(Set.of(3L, 4L))));
    }

    @Test
    void shouldPassStageFilterIntoTheCursorQuery() {
        stubProjectScope(true);
        when(taskMapper.selectTree(any())).thenReturn(List.of());
        ProjectTaskTreeQueryReqVO request = new ProjectTaskTreeQueryReqVO();
        request.setMode("DIRECT_CHILDREN");
        request.setStageCode("S3");

        service.getTasks(100L, request, actor());

        verify(taskMapper).selectTree(argThat((ProjectTaskTreeQuery query) ->
                "S3".equals(query.stageCode())));
    }

    @Test
    void shouldReturnEmptyWhenProjectTreeScopeIsEmpty() {
        stubProjectRecord();
        when(projectTreeScopeService.resolve(any())).thenReturn(new ProjectTreeScopeService.ProjectTreeScope(
                100L, 7L, Set.of(), Set.of(), Set.of()));

        var response = service.getTasks(100L, new ProjectTaskTreeQueryReqVO(), actor());

        assertTrue(response.getRows().isEmpty());
        verify(taskMapper, never()).selectTree(any());
    }

    @Test
    void tenantSuperAdminSeesUnassignedTasksWithoutBeingAProjectMember() {
        stubProjectScope(false);
        when(projectTreeScopeService.isTenantSuperAdmin(0L, 9L)).thenReturn(true);
        when(taskMapper.selectTree(any())).thenReturn(List.of(task(11L, null, 0)));
        var result = service.getTasks(100L, new ProjectTaskTreeQueryReqVO(), actor());
        assertEquals("Task 11", result.getRows().getFirst().getName());
        verify(taskMapper, never()).selectFullTaskIds(any());
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:create")).thenReturn(true);
        assertEquals(Set.of("CREATE"), service.getWorkspace(100L, actor()).getAllowedActions());
    }

    @Test
    void superAdminDoesNotBypassTheProjectTenantOrVisibilityBoundary() {
        var foreign = new ProjectMasterDO(); foreign.setId(100L); foreign.setTenantId(1L);
        when(projectMapper.selectById(100L)).thenReturn(foreign);
        var result = service.getTasks(100L, new ProjectTaskTreeQueryReqVO(), actor());
        assertTrue(result.getRows().isEmpty());
        verify(projectTreeScopeService, never()).isTenantSuperAdmin(any(), any());
        verify(taskMapper, never()).selectTree(any());
    }

    @Test
    void shouldFailClosedForUnregisteredBindingAndRejectCrossTenantTask() {
        stubProjectScope(true);
        ProjectTaskInstanceDO task = task(11L, null, 0);
        when(taskMapper.selectTask(any())).thenReturn(task);
        when(assignmentMapper.selectCurrent(any())).thenReturn(List.of());
        ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
        contract.setId(91L);
        contract.setTenantId(0L);
        contract.setProjectTaskId(11L);
        contract.setWorkBindingTypeCode("BUSINESS_OBJECT");
        contract.setContractVersion(1);
        when(contractMapper.selectCurrentByTaskId(11L)).thenReturn(contract);
        when(bindingRegistry.inspect(any(), any())).thenReturn(
                TaskBindingInspection.failed("BUSINESS_OBJECT", "BINDING_PROVIDER_UNREGISTERED"));

        var response = service.getWorkbench(11L, actor());
        assertEquals("BINDING_PROVIDER_UNREGISTERED", response.getRecoverableError());
        assertTrue(response.getAllowedActions().isEmpty());

        when(taskMapper.selectTask(any())).thenReturn(null);
        assertThrows(RuntimeException.class,
                () -> service.getTask(992L, new TaskWorkbenchActor(1L, 9L, "cross-tenant")));
    }

    @Test
    void shouldExposeOnlyServerApprovedWorkspaceAndWorkbenchActions() {
        stubProjectScope(true);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:create")).thenReturn(true);

        assertEquals(Set.of("CREATE"), service.getWorkspace(100L, actor()).getAllowedActions());

        ProjectTaskInstanceDO task = task(11L, null, 0);
        when(taskMapper.selectTask(any())).thenReturn(task);
        when(assignmentMapper.selectCurrent(any())).thenReturn(List.of());
        ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
        contract.setId(91L);
        contract.setTenantId(0L);
        contract.setProjectTaskId(11L);
        contract.setWorkBindingTypeCode("TASK_NATIVE");
        contract.setContractVersion(1);
        when(contractMapper.selectCurrentByTaskId(11L)).thenReturn(contract);
        when(bindingRegistry.inspect(any(), any())).thenReturn(
                new TaskBindingInspection("TASK_NATIVE", Set.of("START"), "0:1:0", null));
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:update")).thenReturn(true);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:move")).thenReturn(true);

        assertEquals(Set.of("START", "UPDATE", "MOVE"),
                service.getWorkbench(11L, actor()).getAllowedActions());
    }

    private void stubProjectScope(boolean manager) {
        stubProjectRecord();
        when(projectTreeScopeService.resolve(any())).thenReturn(new ProjectTreeScopeService.ProjectTreeScope(
                100L, 7L, Set.of(100L), Set.of(), Set.of()));
        if (manager) {
            ProjectMemberAssignmentDO membership = new ProjectMemberAssignmentDO();
            membership.setProjectId(100L);
            membership.setMemberRole("PROJECT_MANAGER");
            when(memberMapper.selectActiveByUser(any())).thenReturn(List.of(membership));
        } else {
            when(memberMapper.selectActiveByUser(any())).thenReturn(List.of());
        }
    }

    @Test
    void businessBindingKeepsGenericAssignmentWithPermissionAndStateChecks() {
        stubProjectScope(true);
        var task = task(11L, null, 0); task.setStatus("PENDING_ASSIGN");
        when(taskMapper.selectTask(any())).thenReturn(task);
        var contract = new ProjectTaskExecutionContractDO();
        contract.setId(91L); contract.setTenantId(0L); contract.setProjectTaskId(11L);
        contract.setWorkBindingTypeCode("BUSINESS_OBJECT"); contract.setContractVersion(1);
        when(contractMapper.selectCurrentByTaskId(11L)).thenReturn(contract);
        when(bindingRegistry.inspect(any(), any())).thenReturn(new TaskBindingInspection("BUSINESS_OBJECT", Set.of(), "1", null));
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:update")).thenReturn(false);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:move")).thenReturn(false);
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:assign")).thenReturn(true);
        assertEquals(Set.of("ASSIGN"), service.getWorkbench(11L, actor()).getAllowedActions());
        task.setStatus("DONE");
        assertTrue(service.getWorkbench(11L, actor()).getAllowedActions().isEmpty());
        task.setStatus("PENDING_ASSIGN");
        when(permissionApi.hasAnyPermissions(9L, "pms:project-task:assign")).thenReturn(false);
        assertTrue(service.getWorkbench(11L, actor()).getAllowedActions().isEmpty());
    }

    private void stubProjectRecord() {
        ProjectMasterDO project = new ProjectMasterDO();
        project.setId(100L);
        project.setRootId(100L);
        project.setTenantId(0L);
        project.setTaskTreeVersion(3L);
        project.setLifecycleStatus("ACTIVE");
        when(projectMapper.selectById(100L)).thenReturn(project);
        ProjectTreeVersionDO version = new ProjectTreeVersionDO();
        version.setTreeVersion(7L);
        when(projectTreeVersionMapper.selectLatestActive(100L)).thenReturn(version);
    }

    private ProjectTaskInstanceDO task(Long id, Long parentId, int depth) {
        ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
        task.setId(id);
        task.setTenantId(0L);
        task.setProjectId(100L);
        task.setParentTaskId(parentId);
        task.setRootTaskId(parentId == null ? id : 1L);
        task.setTreeDepth(depth);
        task.setTaskCode("T-" + id);
        task.setName("Task " + id);
        task.setStageCode("S1");
        task.setStatus("PENDING_START");
        task.setSortOrder(id.intValue());
        task.setVersion(0);
        task.setStateMachineRevisionId(81L);
        return task;
    }

    private TaskWorkbenchActor actor() {
        return new TaskWorkbenchActor(0L, 9L, "test");
    }
}
