package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectCreateReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectMatchTemplatesRespVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectRespVO;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * F-PM01 T4：项目手工创建 API 契约测试。
 * <p>
 * 逐端点校验技术计划第3节契约：HTTP 方法 + 路径 + 权限注解（与 V57 菜单 18067~18070 权限串一致），
 * 以及创建端点的 Idempotency-Key 头声明。新链复数路由 /pms/projects，防回落旧单数 /pms/project。
 */
class ProjectMasterControllerContractTest {

    private static final String BASE = "/pms/projects";

    @Test
    void classLevelRouteMatchesContract() {
        RequestMapping rm = ProjectMasterController.class.getAnnotation(RequestMapping.class);
        assertNotNull(rm, "Controller 缺少 @RequestMapping");
        assertEquals(1, rm.value().length);
        assertEquals(BASE, rm.value()[0]);
    }

    @Test
    void createEndpointDeclaresIdempotencyKeyHeader() {
        Method method = findMethod("createProject");
        Parameter keyParameter = java.util.Arrays.stream(method.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(RequestHeader.class))
                .findFirst().orElse(null);
        assertNotNull(keyParameter, "createProject 缺少 @RequestHeader Idempotency-Key 参数");
        RequestHeader header = keyParameter.getAnnotation(RequestHeader.class);
        assertEquals("Idempotency-Key", header.value(), "幂等头名称不符合契约");
        assertTrue(header.required(), "正式创建必须提供幂等头");
    }

    @Test
    void createContractUsesRevisionIdAndRequiredCandidateWatermark() throws Exception {
        assertNotNull(ProjectCreateReqVO.class.getDeclaredField("templateRevisionId"));
        var watermark = ProjectCreateReqVO.class.getDeclaredField("candidateWatermark");
        assertNotNull(watermark.getAnnotation(NotBlank.class), "候选水位必须由边界校验为非空");
        assertNotNull(ProjectMatchTemplatesRespVO.class.getDeclaredField("candidateWatermark"));
        assertNotNull(ProjectMatchTemplatesRespVO.CandidateItem.class
                .getDeclaredField("templateRevisionId"));
        assertThrowsNoField(ProjectCreateReqVO.class, "templateId");
    }

    @Test
    void createEndpoint() {
        assertEndpoint("createProject", PostMapping.class, "", "pms:project:create");
    }

    @Test
    void matchTemplatesEndpoint() {
        assertEndpoint("matchTemplates", GetMapping.class, "/actions/match-templates", "pms:project:create");
    }

    @Test
    void pageEndpoint() {
        assertEndpoint("getProjectPage", GetMapping.class, "/page", "pms:project:query");
    }

    @Test
    void detailEndpoint() {
        assertEndpoint("getProject", GetMapping.class, "/{id}", "pms:project:query");
    }

    @Test
    void updateEndpoint() {
        assertEndpoint("updateProject", PutMapping.class, "/{id}", "pms:project:update");
    }

    @Test
    void instancesEndpoint() {
        assertEndpoint("getProjectInstances", GetMapping.class, "/{id}/instances", "pms:project:query");
    }

    @Test
    void membersEndpoint() {
        assertEndpoint("getProjectMembers", GetMapping.class, "/{id}/members", "pms:project:query");
    }

    @Test
    void assignManagerEndpoint() {
        assertEndpoint("assignManager", PostMapping.class, "/{id}/actions/assign-manager", "pms:project:assign");
        assertRequiredHeader("assignManager", "Idempotency-Key");
        assertRequiredHeader("assignManager", "If-Match");
    }

    @Test
    void updateChildWeightsEndpoint() {
        assertEndpoint("updateChildWeights", PutMapping.class, "/{id}/child-weights", "pms:project:update");
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
        for (Method method : ProjectMasterController.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return fail("未找到方法：" + methodName);
    }

    private static String extractPath(Annotation mapping) {
        if (mapping instanceof GetMapping get) {
            return get.value().length > 0 ? get.value()[0] : "";
        }
        if (mapping instanceof PostMapping post) {
            return post.value().length > 0 ? post.value()[0] : "";
        }
        if (mapping instanceof PutMapping put) {
            return put.value().length > 0 ? put.value()[0] : "";
        }
        return fail("未覆盖的映射类型：" + mapping.annotationType());
    }

    @Test
    void projectDetailExposesVersionForAssignmentIfMatch() throws Exception {
        assertNotNull(ProjectRespVO.class.getDeclaredField("version"));
        assertNotNull(ProjectRespVO.class.getDeclaredField("assignmentStatus"));
    }

    private static void assertThrowsNoField(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            fail(type.getSimpleName() + " 不得继续暴露旧字段 " + name);
        } catch (NoSuchFieldException expected) {
            // 符合V1.8 revision级契约。
        }
    }

    private static void assertRequiredHeader(String methodName, String headerName) {
        Method method = findMethod(methodName);
        RequestHeader header = java.util.Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(annotation -> annotation != null && headerName.equals(annotation.value()))
                .findFirst().orElse(null);
        assertNotNull(header, methodName + " 缺少 @RequestHeader " + headerName + " 参数");
        assertTrue(header.required(), headerName + " 必须是必填请求头");
    }
}
