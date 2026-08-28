package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectWorkspaceRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.ProjectTaskUpdateReqVO;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskQueryService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskCommandService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskLifecycleService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskAssignmentService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.ProjectTaskProgressService;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskWorkbenchActor;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.TaskCommandResult;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.command.UpdateTaskProgressCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_SCOPE_FORBIDDEN;

class ProjectTaskWorkbenchControllerTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExposeLockedReadRoutesWithSharedPermissionTruth() throws Exception {
        RequestMapping root = ProjectTaskWorkbenchController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/api/v1/pms"}, root.value());
        Map<String, String> routes = Map.of(
                "getWorkspace", "/projects/{id}/workspace",
                "getTasks", "/projects/{id}/tasks",
                "getTask", "/project-tasks/{id}",
                "getWorkbench", "/project-tasks/{id}/workbench");
        for (Map.Entry<String, String> entry : routes.entrySet()) {
            Method method = findMethod(entry.getKey());
            assertArrayEquals(new String[]{entry.getValue()}, method.getAnnotation(GetMapping.class).value());
            assertEquals("@ss.hasPermission('pms:project-task:query')",
                    method.getAnnotation(PreAuthorize.class).value());
        }
    }

    @Test
    void shouldExposeLockedTaskFiveCommandRoutes() {
        assertRoute("createTask", PostMapping.class, "/projects/{id}/tasks", "pms:project-task:create");
        assertRoute("updateTask", PatchMapping.class, "/project-tasks/{id}",
                "@ss.hasPermission('pms:project-task:update') or @ss.hasPermission('pms:project-task:execute')");
        assertRoute("moveTask", PostMapping.class, "/project-tasks/{id}/actions/move", "pms:project-task:move");
        assertRoute("addDependency", PostMapping.class, "/project-tasks/{id}/dependencies", "pms:project-task:move");
    }

    @Test
    void shouldExposeTaskSixCandidateRoute() {
        assertRoute("getAssigneeCandidates", GetMapping.class,
                "/project-tasks/{id}/assignee-candidates", "pms:project-task:assign");
        assertRoute("assignTask", PostMapping.class,
                "/project-tasks/{id}/actions/assign", "pms:project-task:assign");
    }

    @Test
    void shouldExposeTaskSevenLifecycleRoute() {
        assertRoute("actTask", PostMapping.class,
                "/project-tasks/{id}/actions/{action}",
                "@ss.hasPermission('pms:project-task:execute') or @ss.hasPermission('pms:project-task:complete')");
    }

    @Test
    void singleTenantHttpQueryEstablishesTrustedTenantZeroForCallOnly() throws Exception {
        ProjectTaskQueryService queryService = mock(ProjectTaskQueryService.class);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("yudao.tenant.enable", Boolean.class, true)).thenReturn(false);
        when(queryService.getWorkspace(eq(100L), any())).thenAnswer(invocation -> {
            assertEquals(0L, TenantContextHolder.getRequiredTenantId());
            TaskWorkbenchActor actor = invocation.getArgument(1);
            assertEquals(0L, actor.tenantId());
            return new ProjectWorkspaceRespVO();
        });
        MockMvc mvc = standaloneSetup(new ProjectTaskWorkbenchController(
                queryService, mock(ProjectTaskCommandService.class),
                mock(ProjectTaskAssignmentService.class), mock(ProjectTaskLifecycleService.class),
                mock(ProjectTaskProgressService.class), environment)).build();

        mvc.perform(get("/api/v1/pms/projects/100/workspace"))
                .andExpect(status().isOk());

        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void defaultEnabledMultiTenantHttpQueryWithoutContextFailsClosed() {
        ProjectTaskQueryService queryService = mock(ProjectTaskQueryService.class);
        Environment environment = new MockEnvironment();
        MockMvc mvc = standaloneSetup(new ProjectTaskWorkbenchController(
                queryService, mock(ProjectTaskCommandService.class),
                mock(ProjectTaskAssignmentService.class), mock(ProjectTaskLifecycleService.class),
                mock(ProjectTaskProgressService.class), environment)).build();

        Exception error = assertThrows(Exception.class,
                () -> mvc.perform(get("/api/v1/pms/projects/100/workspace")));

        ServiceException serviceException = assertInstanceOf(ServiceException.class, error.getCause());
        assertEquals(PROJECT_TASK_SCOPE_FORBIDDEN.getCode(), serviceException.getCode());
        verifyNoInteractions(queryService);
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void progressOnlyPatchDispatchesToProgressService() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(9L);
        loginUser.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
        ProjectTaskProgressService progressService = mock(ProjectTaskProgressService.class);
        when(progressService.updateProgress(any(), any())).thenReturn(
                new TaskCommandResult(11L, 4, 5L, "IN_PROGRESS", "NEW"));
        Environment environment = mock(Environment.class);
        when(environment.getProperty("yudao.tenant.enable", Boolean.class, true)).thenReturn(false);
        ProjectTaskWorkbenchController controller = new ProjectTaskWorkbenchController(
                mock(ProjectTaskQueryService.class), mock(ProjectTaskCommandService.class),
                mock(ProjectTaskAssignmentService.class), mock(ProjectTaskLifecycleService.class),
                progressService, environment);
        ProjectTaskUpdateReqVO request = new ProjectTaskUpdateReqVO();
        request.setProgress(60);

        controller.updateTask(11L, "\"3\"", request);

        verify(progressService).updateProgress(eq(new UpdateTaskProgressCommand(11L, 3, 60)), any());
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void mixedProgressAndBasicPatchIsRejectedBeforeEitherCommandRuns() {
        ProjectTaskCommandService commandService = mock(ProjectTaskCommandService.class);
        ProjectTaskProgressService progressService = mock(ProjectTaskProgressService.class);
        Environment environment = mock(Environment.class);
        when(environment.getProperty("yudao.tenant.enable", Boolean.class, true)).thenReturn(false);
        ProjectTaskWorkbenchController controller = new ProjectTaskWorkbenchController(
                mock(ProjectTaskQueryService.class), commandService,
                mock(ProjectTaskAssignmentService.class), mock(ProjectTaskLifecycleService.class),
                progressService, environment);
        ProjectTaskUpdateReqVO request = new ProjectTaskUpdateReqVO();
        request.setProgress(60);
        request.setName("mixed");

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.updateTask(11L, "3", request));

        assertEquals(cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TASK_COMMAND_INVALID.getCode(),
                error.getCode());
        verifyNoInteractions(commandService, progressService);
    }

    private Method findMethod(String name) {
        return java.util.Arrays.stream(ProjectTaskWorkbenchController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name)).findFirst().orElseThrow();
    }

    private <A extends java.lang.annotation.Annotation> void assertRoute(
            String methodName, Class<A> annotationType, String route, String permission) {
        Method method = findMethod(methodName);
        String[] values;
        if (annotationType == PostMapping.class) values = method.getAnnotation(PostMapping.class).value();
        else if (annotationType == PatchMapping.class) values = method.getAnnotation(PatchMapping.class).value();
        else values = method.getAnnotation(GetMapping.class).value();
        assertArrayEquals(new String[]{route}, values);
        String expected = permission.startsWith("@ss.") ? permission : "@ss.hasPermission('" + permission + "')";
        assertEquals(expected, method.getAnnotation(PreAuthorize.class).value());
    }
}
