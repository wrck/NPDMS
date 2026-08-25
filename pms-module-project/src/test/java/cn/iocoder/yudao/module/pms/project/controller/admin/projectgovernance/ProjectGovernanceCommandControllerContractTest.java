package cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance;

import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectExceptionCloseReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectReopenReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectgovernance.vo.ProjectRollbackReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
