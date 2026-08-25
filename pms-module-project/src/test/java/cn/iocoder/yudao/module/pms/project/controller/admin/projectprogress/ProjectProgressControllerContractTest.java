package cn.iocoder.yudao.module.pms.project.controller.admin.projectprogress;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ProjectProgressControllerContractTest {

    @Test
    void formalEndpointsMatchFeatureContract() {
        assertEndpoint("createPolicy", PostMapping.class, "/projects/{projectId}/progress-policies",
                "pms:project:progress-policy:update");
        assertEndpoint("submitPolicy", PostMapping.class, "/progress-policies/{revisionId}/actions/submit",
                "pms:project:progress-policy:submit");
        assertEndpoint("listPolicies", GetMapping.class, "/projects/{projectId}/progress-policies",
                "pms:project:query");
        assertEndpoint("getProgress", GetMapping.class, "/projects/{projectId}/progress",
                "pms:project:query");
    }

    @Test
    void submitRequiresOptimisticLockHeader() {
        Method method = findMethod("submitPolicy");
        assertTrue(java.util.Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .anyMatch(header -> header != null && "If-Match".equals(header.value()) && header.required()));
    }

    private static void assertEndpoint(String methodName, Class<? extends Annotation> mappingType,
                                       String path, String permission) {
        Method method = findMethod(methodName);
        Annotation mapping = method.getAnnotation(mappingType);
        assertNotNull(mapping);
        String actualPath = mapping instanceof GetMapping get ? get.value()[0] : ((PostMapping) mapping).value()[0];
        assertEquals(path, actualPath);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("@ss.hasPermission('" + permission + "')", preAuthorize.value());
    }

    private static Method findMethod(String name) {
        for (Method method : ProjectProgressController.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) return method;
        }
        return fail("未找到方法：" + name);
    }
}
