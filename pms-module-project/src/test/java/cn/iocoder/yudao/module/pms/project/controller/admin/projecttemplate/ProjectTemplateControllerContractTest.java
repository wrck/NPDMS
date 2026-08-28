package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F-PM03 T4：项目模板 API 契约测试。
 * <p>
 * 逐端点校验技术计划第3节契约：HTTP 方法 + 路径 + 权限注解（与 V52 菜单 18060~18066 权限串一致）。
 */
class ProjectTemplateControllerContractTest {

    private static final String BASE = "/pms/project-templates";

    @Test
    void classLevelRouteMatchesContract() {
        RequestMapping rm = ProjectTemplateController.class.getAnnotation(RequestMapping.class);
        assertNotNull(rm, "Controller 缺少 @RequestMapping");
        assertEquals(1, rm.value().length);
        assertEquals(BASE, rm.value()[0]);
    }

    @Test
    void pageEndpoint() {
        assertEndpoint("getProjectTemplatePage", GetMapping.class, "/page", "pms:project-template:query");
    }

    @Test
    void createEndpoint() {
        assertEndpoint("createProjectTemplate", PostMapping.class, "", "pms:project-template:create");
    }

    @Test
    void updateEndpoint() {
        assertEndpoint("updateProjectTemplate", PutMapping.class, "/{id}", "pms:project-template:update");
    }

    @Test
    void deleteEndpoint() {
        assertEndpoint("deleteProjectTemplate", DeleteMapping.class, "/{id}", "pms:project-template:delete");
    }

    @Test
    void detailEndpoint() {
        assertEndpoint("getProjectTemplate", GetMapping.class, "/{id}", "pms:project-template:query");
    }

    @Test
    void publishEndpoint() {
        assertEndpoint("publishProjectTemplate", PostMapping.class, "/{id}/actions/publish",
                "pms:project-template:publish");
    }

    @Test
    void disableEndpoint() {
        assertEndpoint("disableProjectTemplate", PostMapping.class, "/{id}/actions/disable",
                "pms:project-template:disable");
    }

    @Test
    void revisionDetailEndpoint() {
        assertEndpoint("getProjectTemplateRevision", GetMapping.class, "/{id}/revisions/{revisionNo}",
                "pms:project-template:query");
    }

    @Test
    void matchPreviewEndpoint() {
        assertEndpoint("matchPreview", PostMapping.class, "/actions/match-preview",
                "pms:project-template:query");
    }

    // ========== 断言辅助 ==========

    private static void assertEndpoint(String methodName, Class<? extends Annotation> httpAnnotation,
                                       String expectedPath, String expectedPermission) {
        Method method = findMethod(methodName);
        Annotation mapping = method.getAnnotation(httpAnnotation);
        assertNotNull(mapping, methodName + " 缺少 " + httpAnnotation.getSimpleName());
        assertEquals(expectedPath, extractPath(mapping), methodName + " 路径不符合契约");
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize, methodName + " 缺少 @PreAuthorize");
        assertEquals("@ss.hasPermission('" + expectedPermission + "')", preAuthorize.value(),
                methodName + " 权限串不符合契约");
    }

    private static Method findMethod(String methodName) {
        for (Method method : ProjectTemplateController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        fail("未找到端点方法：" + methodName);
        return null;
    }

    private static String extractPath(Annotation mapping) {
        try {
            String[] value = (String[]) mapping.annotationType().getMethod("value").invoke(mapping);
            return value.length == 0 ? "" : value[0];
        } catch (Exception e) {
            throw new IllegalStateException("读取映射路径失败", e);
        }
    }
}
