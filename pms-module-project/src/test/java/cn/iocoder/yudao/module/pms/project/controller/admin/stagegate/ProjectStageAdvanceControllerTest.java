package cn.iocoder.yudao.module.pms.project.controller.admin.stagegate;

import cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo.ProjectStageAdvanceReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.stagegate.vo.ProjectStageGateProcessStartReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProjectStageAdvanceControllerTest {

    @Test
    void routesAndPermissionsMatchTheFeatureContract() {
        RequestMapping mapping = ProjectStageAdvanceController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertEquals("/api/v1/pms/projects", mapping.value()[0]);
        assertEndpoint("readiness", GetMapping.class, "/{id}/stage-advance-readiness", "pms:project:query");
        assertEndpoint("processDefinitions", GetMapping.class,
                "/{id}/stage-gates/{gateReferenceId}/process-definitions", "pms:project:update");
        assertEndpoint("startProcess", PostMapping.class,
                "/{id}/stage-gates/{gateReferenceId}/actions/start-process", "pms:project:update");
        assertEndpoint("advance", PostMapping.class, "/{id}/actions/advance-stage", "pms:project:update");
    }

    @Test
    void writeCommandsRequireIfMatchAndIdempotencyHeaders() {
        for (String method : new String[]{"startProcess", "advance"}) {
            assertRequiredHeader(method, "If-Match");
            assertRequiredHeader(method, "Idempotency-Key");
        }
    }

    @Test
    void requestBodiesCannotOverrideTrustedInputs() {
        for (Class<?> type : new Class[]{ProjectStageGateProcessStartReqVO.class, ProjectStageAdvanceReqVO.class}) {
            for (String field : new String[]{"tenantId", "projectId", "actorUserId", "idempotencyKey",
                    "expectedProjectVersion", "targetStage"}) {
                assertNoField(type, field);
            }
        }
    }

    private static void assertEndpoint(String methodName, Class<? extends Annotation> mappingType,
                                       String path, String permission) {
        Method method = findMethod(methodName);
        Annotation annotation = method.getAnnotation(mappingType);
        assertNotNull(annotation, methodName + " 缺少HTTP映射");
        assertEquals(path, path(annotation));
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorization, methodName + " 缺少功能权限");
        assertEquals("@ss.hasPermission('" + permission + "')", authorization.value());
    }

    private static void assertRequiredHeader(String methodName, String headerName) {
        RequestHeader header = Arrays.stream(findMethod(methodName).getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(annotation -> annotation != null && headerName.equals(annotation.value()))
                .findFirst().orElse(null);
        assertNotNull(header, methodName + " 缺少" + headerName);
        assertTrue(header.required(), headerName + "必须为必填头");
    }

    private static Method findMethod(String name) {
        return Arrays.stream(ProjectStageAdvanceController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst().orElseGet(() -> fail("未找到方法：" + name));
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
            // 受信信息只从路径、Header和服务端上下文进入。
        }
    }
}
