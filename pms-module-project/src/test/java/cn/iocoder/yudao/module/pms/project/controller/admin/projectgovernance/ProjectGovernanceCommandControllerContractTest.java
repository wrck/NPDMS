package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectExceptionCloseReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectReopenReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectRollbackReqVO;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceGuardResult;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceGuardService;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.ProjectGovernanceHistoryQueryService;
import cn.iocoder.yudao.module.pms.project.service.projectgovernance.command.GovernanceActionResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;

class ProjectGovernanceCommandControllerContractTest {

    @Test
    void routesAndPermissionsMatchLockedContract() {
        RequestMapping mapping = ProjectGovernanceCommandController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertEquals("/pms/projects", mapping.value()[0]);
        assertEndpoint("getGuard", GetMapping.class, "/{id}/governance-guard",
                "pms:project:governance:query");
        assertEndpoint("rollback", PostMapping.class, "/{id}/actions/rollback", "pms:project:rollback");
        assertEndpoint("close", PostMapping.class, "/{id}/actions/close", "pms:project:close");
        assertEndpoint("reopen", PostMapping.class, "/{id}/actions/reopen", "pms:project:reopen");
        assertEndpoint("getHistory", GetMapping.class, "/{id}/governance-history",
                "pms:project:governance:query");
    }

    @Test
    void everyCommandRequiresIdempotencyAndIfMatchHeaders() {
        for (String method : new String[]{"rollback", "close", "reopen"}) {
            assertRequiredHeader(method, "Idempotency-Key");
            assertRequiredHeader(method, "If-Match");
        }
    }

    @Test
    void requestBodiesCannotOverrideTrustedPathHeaderOrTenantInputs() {
        for (Class<?> requestType : new Class[]{
                ProjectRollbackReqVO.class, ProjectExceptionCloseReqVO.class, ProjectReopenReqVO.class}) {
            assertNoField(requestType, "tenantId");
            assertNoField(requestType, "projectId");
            assertNoField(requestType, "expectedVersion");
            assertNoField(requestType, "idempotencyKey");
        }
    }

    @Test
    void legacyControllerNoLongerExposesWriteRoutes() {
        for (Method method : ProjectGovernanceController.class.getDeclaredMethods()) {
            assertFalse(method.isAnnotationPresent(PostMapping.class), method.getName());
            assertFalse(method.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class),
                    method.getName());
            assertFalse(method.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class),
                    method.getName());
        }
    }

    @Test
    void singleTenantHttpGuardAndRollbackEstablishTrustedTenantZero() throws Exception {
        TenantContextHolder.clear();
        LoginUser loginUser = new LoginUser();
        loginUser.setId(9L);
        loginUser.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
        try {
            ProjectGovernanceGuardService guardService = mock(ProjectGovernanceGuardService.class);
            ProjectGovernanceApplicationService applicationService = mock(ProjectGovernanceApplicationService.class);
            ProjectGovernanceHistoryQueryService historyService = mock(ProjectGovernanceHistoryQueryService.class);
            Environment environment = mock(Environment.class);
            when(environment.getProperty("yudao.tenant.enable", Boolean.class, true)).thenReturn(false);
            LocalDateTime operatedAt = LocalDateTime.of(2026, 8, 25, 12, 0);
            when(guardService.evaluate(eq(11L), eq(ProjectGovernanceGuardService.GovernanceAction.ROLLBACK), any()))
                    .thenAnswer(invocation -> {
                        assertEquals(0L, TenantContextHolder.getRequiredTenantId());
                        ProjectGovernanceGuardService.Actor actor = invocation.getArgument(2);
                        assertEquals(0L, actor.tenantId());
                        return new ProjectGovernanceGuardResult(11L, 3, "ACTIVE", "S3", "ASSIGNED",
                                10L, 5L, "ROLLBACK", true, "guard-token", List.of(), List.of(), operatedAt);
                    });
            when(applicationService.rollback(any(), any())).thenAnswer(invocation -> {
                assertEquals(0L, TenantContextHolder.getRequiredTenantId());
                ProjectGovernanceGuardService.Actor actor = invocation.getArgument(1);
                assertEquals(0L, actor.tenantId());
                return new GovernanceActionResult(11L, "ROLLBACK", "ACTIVE", "S3", "ASSIGNED",
                        "ACTIVE", "S0", "UNASSIGNED", 4, 101L, "op-1", operatedAt, false);
            });
            MockMvc mvc = standaloneSetup(new ProjectGovernanceCommandController(
                    guardService, applicationService, historyService, environment)).build();

            mvc.perform(get("/pms/projects/11/governance-guard")
                            .param("action", "ROLLBACK"))
                    .andExpect(status().isOk());
            assertNull(TenantContextHolder.getTenantId());
            mvc.perform(post("/pms/projects/11/actions/rollback")
                            .header("Idempotency-Key", "idem-1")
                            .header("If-Match", "3")
                            .contentType("application/json")
                            .content("""
                                    {"guardToken":"guard-token","reasonCode":"CORRECTION",
                                     "reasonDetail":"回退修正","reassignmentRequirement":"重新指派"}
                                    """))
                    .andExpect(status().isOk());
            assertNull(TenantContextHolder.getTenantId());
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void enabledMultiTenantWithoutTrustedContextFailsClosed() {
        TenantContextHolder.clear();
        Environment environment = mock(Environment.class);
        when(environment.getProperty("yudao.tenant.enable", Boolean.class, true)).thenReturn(true);
        ProjectGovernanceCommandController controller = new ProjectGovernanceCommandController(
                mock(ProjectGovernanceGuardService.class), mock(ProjectGovernanceApplicationService.class),
                mock(ProjectGovernanceHistoryQueryService.class), environment);

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.getGuard(11L, ProjectGovernanceGuardService.GovernanceAction.ROLLBACK, 1, 20));

        assertEquals(PROJECT_TREE_SCOPE_FORBIDDEN.getCode(), error.getCode());
    }

    private static void assertEndpoint(String methodName, Class<? extends Annotation> mappingType,
                                       String path, String permission) {
        Method method = findMethod(methodName);
        Annotation mapping = method.getAnnotation(mappingType);
        assertNotNull(mapping, methodName + " 缺少HTTP映射");
        assertEquals(path, path(mapping));
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize, methodName + " 缺少功能权限");
        assertEquals("@ss.hasPermission('" + permission + "')", preAuthorize.value());
    }

    private static void assertRequiredHeader(String methodName, String headerName) {
        RequestHeader header = java.util.Arrays.stream(findMethod(methodName).getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(annotation -> annotation != null && headerName.equals(annotation.value()))
                .findFirst().orElse(null);
        assertNotNull(header, methodName + " 缺少" + headerName);
        assertTrue(header.required(), headerName + "必须为必填头");
    }

    private static Method findMethod(String methodName) {
        return java.util.Arrays.stream(ProjectGovernanceCommandController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst().orElseGet(() -> fail("未找到方法：" + methodName));
    }

    private static String path(Annotation annotation) {
        if (annotation instanceof GetMapping get) return get.value()[0];
        if (annotation instanceof PostMapping post) return post.value()[0];
        return fail("未支持的HTTP映射");
    }

    private static void assertNoField(Class<?> type, String field) {
        try {
            type.getDeclaredField(field);
            fail(type.getSimpleName() + "不得暴露" + field);
        } catch (NoSuchFieldException expected) {
            // 受信信息只从服务端上下文、路径和Header进入。
        }
    }
}
