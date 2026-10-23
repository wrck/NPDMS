package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectTaskExecutionContext;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskWorkbenchActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectTaskBusinessRefreshServiceTest {
    private final TaskBusinessAccess access = mock(TaskBusinessAccess.class);
    private final ProjectTaskRuntimeMapper tasks = mock(ProjectTaskRuntimeMapper.class);
    private final ProjectTaskExecutionContractMapper contracts = mock(ProjectTaskExecutionContractMapper.class);
    private final TaskBusinessObjectProvider provider = mock(TaskBusinessObjectProvider.class);
    private final ProjectNodeExecutionApi executions = mock(ProjectNodeExecutionApi.class);
    private final ProjectTaskBusinessAssociationService associations = mock(ProjectTaskBusinessAssociationService.class);
    private final ProjectTaskLifecycleService lifecycle = mock(ProjectTaskLifecycleService.class);
    private final OperationAuditApi audit = mock(OperationAuditApi.class);
    private final TaskWorkbenchActor actor = new TaskWorkbenchActor(1L, 9L, "refresh-test");
    private final ProjectTaskBusinessRefreshService.Command command =
            new ProjectTaskBusinessRefreshService.Command(10L, 3, 2);
    private final ProjectTaskInstanceDO task = new ProjectTaskInstanceDO();
    private final ProjectTaskExecutionContractDO contract = new ProjectTaskExecutionContractDO();
    private ProjectTaskBusinessRefreshService service;

    @BeforeEach void setup() {
        when(provider.ownerContext()).thenReturn("PROJ");
        when(provider.objectType()).thenReturn("PROJECT_ASSIGNMENT");
        service = new ProjectTaskBusinessRefreshService(access, tasks, contracts,
                new TaskBusinessProviderRegistry(List.of(provider)), executions, associations, lifecycle, audit);
        task.setId(10L); task.setTenantId(1L); task.setProjectId(20L); task.setVersion(3); task.setStatus("IN_PROGRESS");
        var project = new ProjectMasterDO(); project.setId(20L); project.setTenantId(1L); project.setLifecycleStatus("ACTIVE");
        contract.setId(30L); contract.setTenantId(1L); contract.setProjectTaskId(10L); contract.setContractVersion(2);
        contract.setTargetContextCode("PROJ"); contract.setTargetObjectType("PROJECT_ASSIGNMENT");
        contract.setComponentKey("assignment"); contract.setWorkBindingTypeCode("BUSINESS_COMPONENT");
        contract.setBindingParameterSnapshot("{\"businessViewRevisionId\":40,\"instanceResolutionStrategy\":\"REFERENCE_EXISTING\"}");
        when(access.read(10L, 1L, 9L)).thenReturn(task);
        when(access.executable(any(), any(), any())).thenReturn(true);
        when(tasks.selectProjectForCommandForUpdate(any())).thenReturn(project);
        when(tasks.selectTaskForAssignmentForUpdate(any())).thenReturn(task);
        when(contracts.selectCurrentByTaskIdForUpdate(any())).thenReturn(contract);
        when(provider.inspectContext(any())).thenReturn(Set.of("QUERY"));
        when(executions.inspect(any())).thenReturn(execution(true));
        when(lifecycle.completeFromBusinessResult(20L, 10L, "refresh-test"))
                .thenReturn(new ProjectTaskLifecycleService.AutomaticResult(true, false));
    }

    private ProjectTaskExecutionContext execution(boolean writable) {
        return new ProjectTaskExecutionContext(20L, 1L, 10L, 3, 30L, 2, 60L, 61L, 1, 2, 62L, 1,
                writable, java.time.LocalDateTime.of(2026, 10, 20, 10, 0));
    }

    @Test void refreshReconcilesCurrentLinksThenUsesNormalCompletionAndAuditsInitiator() {
        assertTrue(service.refresh(command, actor).completed());
        var ordered = inOrder(executions, provider, associations, lifecycle, audit);
        ordered.verify(executions).inspect(any());
        ordered.verify(executions).lockAndRevalidate(any());
        ordered.verify(provider).inspectContext(any());
        ordered.verify(associations).synchronize(20L, 10L, "refresh-test");
        ordered.verify(lifecycle).completeFromBusinessResult(20L, 10L, "refresh-test");
        ordered.verify(audit).record(eq(1L), eq(9L), eq("refresh-test"), eq("PROJECT_TASK_BUSINESS_REFRESH"),
                eq("ProjectTask"), eq("10"), eq("SUCCESS"), anyMap());
    }

    @Test void waitingAndUnknownOwnerFactsAreNotReportedAsCompleted() {
        when(lifecycle.completeFromBusinessResult(anyLong(), anyLong(), anyString()))
                .thenReturn(new ProjectTaskLifecycleService.AutomaticResult(false, false),
                        new ProjectTaskLifecycleService.AutomaticResult(false, true));
        assertFalse(service.refresh(command, actor).completed());
        var unknown = service.refresh(command, actor);
        assertFalse(unknown.completed()); assertTrue(unknown.unknown());
    }

    @Test void revokedAuthorityStopsBeforeAnyBusinessWrite() {
        when(access.executable(any(), any(), any())).thenReturn(false);
        assertThrows(RuntimeException.class, () -> service.refresh(command, actor));
        verifyNoInteractions(associations, lifecycle, audit);
    }

    @Test void staleTaskOrContractVersionsStopBeforeAnyBusinessWrite() {
        task.setVersion(4);
        assertThrows(RuntimeException.class, () -> service.refresh(command, actor));
        task.setVersion(3); contract.setContractVersion(3);
        assertThrows(RuntimeException.class, () -> service.refresh(command, actor));
        verifyNoInteractions(associations, lifecycle, audit);
    }

    @Test void unstartedAndFinishedTasksCannotBeAdvancedByRefresh() {
        for (var status : List.of("PENDING_ASSIGN", "READY", "DONE", "CLOSED")) {
            task.setStatus(status);
            assertThrows(RuntimeException.class, () -> service.refresh(command, actor));
        }
        verifyNoInteractions(associations, lifecycle, audit);
    }

    @Test void staleExecutionOrLostOwnerQueryPermissionStopsBeforeAnyBusinessWrite() {
        when(executions.inspect(any())).thenReturn(execution(false));
        assertThrows(RuntimeException.class, () -> service.refresh(command, actor));
        when(executions.inspect(any())).thenReturn(execution(true));
        when(provider.inspectContext(any())).thenReturn(Set.of());
        assertThrows(RuntimeException.class, () -> service.refresh(command, actor));
        verifyNoInteractions(associations, lifecycle, audit);
    }

    @Test void associationFailureDoesNotAttemptCompletionOrReportSuccess() {
        doThrow(new IllegalStateException("OWNER_ASSOCIATION_UNAVAILABLE")).when(associations).synchronize(anyLong(), anyLong(), anyString());
        assertThrows(RuntimeException.class, () -> service.refresh(command, actor));
        verifyNoInteractions(lifecycle, audit);
    }
}
