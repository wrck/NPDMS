package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerCommand;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.AssignServiceManagerResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PMS_IDEMPOTENCY_KEY_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectManagerAssignmentApplicationServiceTest {

    @Mock
    private ProjectCreationPlatformFactService platformFactService;
    @Mock
    private ProjectManualCreationService projectService;
    @Mock
    private PermissionCommonApi permissionApi;

    @InjectMocks
    private ProjectManagerAssignmentApplicationService service;

    @Test
    @SuppressWarnings("unchecked")
    void authorizedAssignmentRunsInsidePlatformFactBoundary() {
        AssignServiceManagerResult assigned = new AssignServiceManagerResult(1L, 8L, 3, "UNASSIGNED");
        when(permissionApi.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);
        when(projectService.assignServiceManager(any())).thenReturn(assigned);
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Supplier<Object> operation = invocation.getArgument(3);
            Function<Object, ?> facts = invocation.getArgument(4);
            Object result = operation.get();
            facts.apply(result);
            return new ProjectCreationPlatformFactService.ExecutionResult<>(
                    ProjectCreationPlatformFactService.Decision.NEW, result);
        });

        AssignServiceManagerResult result = service.assign(command(), actor());

        assertEquals(8L, result.assignmentId());
        assertEquals(3, result.version());
        verify(projectService).assignServiceManager(any());
    }

    @Test
    void permissionFailureStopsBeforeIdempotencyClaim() {
        when(permissionApi.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assign(command(), actor()));

        assertEquals(FORBIDDEN.getCode(), exception.getCode());
        verifyNoInteractions(platformFactService);
        verify(projectService, never()).assignServiceManager(any());
    }

    @Test
    void idempotencyConflictIsMappedToStableError() {
        when(permissionApi.hasAnyPermissions(7L, "pms:project:assign")).thenReturn(true);
        when(platformFactService.execute(any(), any(), any(), any(), any())).thenReturn(
                new ProjectCreationPlatformFactService.ExecutionResult<>(
                        ProjectCreationPlatformFactService.Decision.CONFLICT, null));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.assign(command(), actor()));

        assertEquals(PMS_IDEMPOTENCY_KEY_CONFLICT.getCode(), exception.getCode());
        verify(projectService, never()).assignServiceManager(any());
    }

    private AssignServiceManagerCommand command() {
        return new AssignServiceManagerCommand(1L, 2, "SERVICE_MANAGER", "L1", 66L,
                20L, 30L, LocalDateTime.now().minusMinutes(1), "assign-key", "b".repeat(64));
    }

    private ProjectManagerAssignmentApplicationService.Actor actor() {
        return new ProjectManagerAssignmentApplicationService.Actor(1L, 7L, "correlation-1");
    }
}
