package cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization;

import cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo.ProjectAuthorizationCreateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo.ProjectAuthorizationPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projectauthorization.vo.ProjectAuthorizationRevokeReqVO;
import jakarta.validation.constraints.AssertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProjectAuthorizationControllerContractTest {

    @Test
    void routesAndPermissionsMatchApprovedContract() {
        RequestMapping mapping = ProjectAuthorizationController.class.getAnnotation(RequestMapping.class);
        assertNotNull(mapping);
        assertEquals("/pms", mapping.value()[0]);
        assertEndpoint("create", PostMapping.class,
                "/projects/{projectId}/authorization-grants", "pms:project:authorization:manage");
        assertEndpoint("page", GetMapping.class,
                "/projects/{projectId}/authorization-grants", "pms:project:authorization:query");
        assertEndpoint("get", GetMapping.class,
                "/project-authorization-grants/{grantId}", "pms:project:authorization:query");
        assertEndpoint("revoke", PostMapping.class,
                "/project-authorization-grants/{grantId}/actions/revoke",
                "pms:project:authorization:revoke");
    }

    @Test
    void commandHeadersAreRequired() {
        assertRequiredHeader("create", "Idempotency-Key");
        assertRequiredHeader("revoke", "Idempotency-Key");
        assertRequiredHeader("revoke", "If-Match");
    }

    @Test
    void requestBodiesCannotExpandScopeWithPathsOrTenantFields() throws Exception {
        assertNoField(ProjectAuthorizationCreateReqVO.class, "tenantId");
        assertNoField(ProjectAuthorizationCreateReqVO.class, "projectIds");
        assertNoField(ProjectAuthorizationCreateReqVO.class, "path");
        assertNoField(ProjectAuthorizationCreateReqVO.class, "depth");
        assertNoField(ProjectAuthorizationPageReqVO.class, "tenantId");
        assertNoField(ProjectAuthorizationPageReqVO.class, "projectIds");
        assertNoField(ProjectAuthorizationRevokeReqVO.class, "expectedVersion");
        assertNotNull(ProjectAuthorizationPageReqVO.class
                .getDeclaredMethod("isPageSizeWithinLimit").getAnnotation(AssertTrue.class));
    }

    private static void assertEndpoint(String methodName, Class<? extends Annotation> annotationType,
                                       String path, String permission) {
        Method method = findMethod(methodName);
        Annotation mapping = method.getAnnotation(annotationType);
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
        assertNotNull(header, methodName + " 缺少请求头 " + headerName);
        assertTrue(header.required(), headerName + " 必须为必填头");
    }

    private static Method findMethod(String name) {
        return java.util.Arrays.stream(ProjectAuthorizationController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst().orElseGet(() -> fail("未找到方法：" + name));
    }

    private static String path(Annotation annotation) {
        if (annotation instanceof GetMapping mapping) return mapping.value()[0];
        if (annotation instanceof PostMapping mapping) return mapping.value()[0];
        return fail("未支持的HTTP注解");
    }

    private static void assertNoField(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            fail(type.getSimpleName() + " 不得暴露字段 " + name);
        } catch (NoSuchFieldException expected) {
            // 契约不接受客户端扩权字段。
        }
    }
}
