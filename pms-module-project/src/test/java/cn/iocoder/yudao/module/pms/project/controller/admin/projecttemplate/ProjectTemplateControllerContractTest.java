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
 * 逐端点校验 HTTP 方法、路径及权限；只读事实目录同时供模板配置与项目计划管理消费。
 */
class ProjectTemplateControllerContractTest {

    private static final String BASE = "/pms/project-templates";

    @Test
    void classLevelRouteMatchesContract() {
        RequestMapping rm = ProjectTemplateController.class.getAnnotation(RequestMapping.class);
        assertNotNull(rm, "Controller 缺少 @RequestMapping");
        assertEquals(java.util.Set.of(BASE, "/api/v1/pms/project-templates"),
                java.util.Set.of(rm.value()));
    }

    @Test
    void pageEndpoint() {
        assertEndpoint("getProjectTemplatePage", GetMapping.class, "", "pms:project-template:query");
        assertEquals(java.util.Set.of("", "/page"), java.util.Set.of(
                findMethod("getProjectTemplatePage").getAnnotation(GetMapping.class).value()));
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

    @Test
    void completionFactCatalogEndpoint() {
        assertEndpointExpression("completionFactCatalog", GetMapping.class, "/actions/completion-fact-catalog",
                "@ss.hasAnyPermissions('pms:project-template:query', 'pms:project-plan:manage')");
    }

    @Test
    void catalogPermissionAllowsEitherConsumerButNotUnrelatedPermissions() {
        String expression = findMethod("completionFactCatalog").getAnnotation(PreAuthorize.class).value();
        var parser = new org.springframework.expression.spel.standard.SpelExpressionParser();
        for (String granted : java.util.List.of("pms:project-template:query", "pms:project-plan:manage",
                "pms:project-template:publish", "pms:project-template:update", "")) {
            var security = org.mockito.Mockito.mock(
                    cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService.class);
            org.mockito.Mockito.when(security.hasAnyPermissions(
                    "pms:project-template:query", "pms:project-plan:manage"))
                    .thenReturn(granted.equals("pms:project-template:query") || granted.equals("pms:project-plan:manage"));
            var context = new org.springframework.expression.spel.support.StandardEvaluationContext();
            context.setBeanResolver((evaluation, name) -> {
                assertEquals("ss", name);
                return security;
            });
            assertEquals(granted.equals("pms:project-template:query") || granted.equals("pms:project-plan:manage"),
                    parser.parseExpression(expression).getValue(context, Boolean.class), granted);
            org.mockito.Mockito.verify(security).hasAnyPermissions(
                    "pms:project-template:query", "pms:project-plan:manage");
            org.mockito.Mockito.verifyNoMoreInteractions(security);
        }
    }

    // ========== 断言辅助 ==========

    private static void assertEndpoint(String methodName, Class<? extends Annotation> httpAnnotation,
                                       String expectedPath, String expectedPermission) {
        assertEndpointExpression(methodName, httpAnnotation, expectedPath,
                "@ss.hasPermission('" + expectedPermission + "')");
    }

    private static void assertEndpointExpression(String methodName, Class<? extends Annotation> httpAnnotation,
                                                String expectedPath, String expectedExpression) {
        Method method = findMethod(methodName);
        Annotation mapping = method.getAnnotation(httpAnnotation);
        assertNotNull(mapping, methodName + " 缺少 " + httpAnnotation.getSimpleName());
        assertEquals(expectedPath, extractPath(mapping), methodName + " 路径不符合契约");
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize, methodName + " 缺少 @PreAuthorize");
        assertEquals(expectedExpression, preAuthorize.value(),
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
