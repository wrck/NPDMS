package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstanceCreateReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstancePageReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormInstancePatchReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormRevisionPatchReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormTemplateCreateReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormTemplatePageReqVO;
import cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo.DynamicFormTemplatePatchReqVO;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormCommandService;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormCommands;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormQueryService;
import cn.iocoder.yudao.module.pms.platform.service.dynamicform.DynamicFormViews;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicFormControllerContractTest {

    private static final Set<String> FORBIDDEN_REQUEST_FIELDS = Set.of(
            "tenantId", "actorUserId", "actorRoleCode", "ownerContext", "objectType", "objectId",
            "instanceCode", "revisionNo", "revisionStatus", "currentPublishedRevisionId",
            "publishedBy", "publishedAt");

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void controllersExposeOnlyTheLockedMethodsPathsAndPermissions() throws Exception {
        assertEquals(List.of("/api/v1/pms"), List.of(DynamicFormTemplateController.class
                .getAnnotation(RequestMapping.class).value()));
        assertEquals(List.of("/api/v1/pms"), List.of(DynamicFormInstanceController.class
                .getAnnotation(RequestMapping.class).value()));

        assertGet(DynamicFormTemplateController.class.getMethod("page", DynamicFormTemplatePageReqVO.class),
                "/dynamic-form-templates", "pms:dynamic-form-template:query");
        assertPost(DynamicFormTemplateController.class.getMethod("create", String.class,
                        DynamicFormTemplateCreateReqVO.class), "/dynamic-form-templates",
                "pms:dynamic-form-template:manage");
        assertGet(DynamicFormTemplateController.class.getMethod("get", Long.class),
                "/dynamic-form-templates/{templateId}", "pms:dynamic-form-template:query");
        assertPatch(DynamicFormTemplateController.class.getMethod("patch", Long.class, Integer.class,
                        DynamicFormTemplatePatchReqVO.class), "/dynamic-form-templates/{templateId}",
                "pms:dynamic-form-template:manage");
        assertPost(DynamicFormTemplateController.class.getMethod("createRevision", Long.class, Integer.class,
                        String.class), "/dynamic-form-templates/{templateId}/revisions",
                "pms:dynamic-form-template:manage");
        assertGet(DynamicFormTemplateController.class.getMethod("getRevision", Long.class),
                "/dynamic-form-template-revisions/{revisionId}", "pms:dynamic-form-template:query");
        assertPatch(DynamicFormTemplateController.class.getMethod("patchRevision", Long.class, Integer.class,
                        DynamicFormRevisionPatchReqVO.class), "/dynamic-form-template-revisions/{revisionId}",
                "pms:dynamic-form-template:manage");
        assertPost(DynamicFormTemplateController.class.getMethod("publish", Long.class, Integer.class,
                        String.class), "/dynamic-form-template-revisions/{revisionId}/actions/publish",
                "pms:dynamic-form-template:publish");
        assertPost(DynamicFormTemplateController.class.getMethod("enable", Long.class, Integer.class, String.class),
                "/dynamic-form-templates/{templateId}/actions/enable", "pms:dynamic-form-template:publish");
        assertPost(DynamicFormTemplateController.class.getMethod("disable", Long.class, Integer.class, String.class),
                "/dynamic-form-templates/{templateId}/actions/disable", "pms:dynamic-form-template:publish");
        assertGet(DynamicFormTemplateController.class.getMethod("selection", DynamicFormTemplatePageReqVO.class),
                "/dynamic-form-templates/selection", "pms:dynamic-form-instance:query");

        assertGet(DynamicFormInstanceController.class.getMethod("page", DynamicFormInstancePageReqVO.class),
                "/dynamic-form-instances", "pms:dynamic-form-instance:query");
        assertPost(DynamicFormInstanceController.class.getMethod("create", String.class,
                        DynamicFormInstanceCreateReqVO.class), "/dynamic-form-instances",
                "pms:dynamic-form-instance:create");
        assertGet(DynamicFormInstanceController.class.getMethod("get", Long.class),
                "/dynamic-form-instances/{instanceId}", "pms:dynamic-form-instance:query");
        assertPatch(DynamicFormInstanceController.class.getMethod("patch", Long.class, Integer.class,
                        DynamicFormInstancePatchReqVO.class), "/dynamic-form-instances/{instanceId}",
                "pms:dynamic-form-instance:update");
    }

    @Test
    void commandHeadersAreExplicitAndThereIsNoRevisionListEndpoint() throws Exception {
        assertHeader(DynamicFormTemplateController.class.getMethod("create", String.class,
                DynamicFormTemplateCreateReqVO.class), 0, "Idempotency-Key");
        assertHeader(DynamicFormTemplateController.class.getMethod("patch", Long.class, Integer.class,
                DynamicFormTemplatePatchReqVO.class), 1, "If-Match");
        Method createRevision = DynamicFormTemplateController.class.getMethod(
                "createRevision", Long.class, Integer.class, String.class);
        assertHeader(createRevision, 1, "If-Match");
        assertHeader(createRevision, 2, "Idempotency-Key");
        assertHeader(DynamicFormTemplateController.class.getMethod("patchRevision", Long.class, Integer.class,
                DynamicFormRevisionPatchReqVO.class), 1, "If-Match");
        for (String methodName : List.of("publish", "enable", "disable")) {
            Method method = DynamicFormTemplateController.class.getMethod(
                    methodName, Long.class, Integer.class, String.class);
            assertHeader(method, 1, "If-Match");
            assertHeader(method, 2, "Idempotency-Key");
        }
        assertHeader(DynamicFormInstanceController.class.getMethod("create", String.class,
                DynamicFormInstanceCreateReqVO.class), 0, "Idempotency-Key");
        assertHeader(DynamicFormInstanceController.class.getMethod("patch", Long.class, Integer.class,
                DynamicFormInstancePatchReqVO.class), 1, "If-Match");

        Set<String> getPaths = Arrays.stream(DynamicFormTemplateController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class)).filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Stream.of(mapping.value())).collect(java.util.stream.Collectors.toSet());
        assertFalse(getPaths.contains("/dynamic-form-templates/{templateId}/revisions"));
        assertFalse(getPaths.contains("/dynamic-form-template-revisions"));
    }

    @Test
    void requestBodiesCannotSelfReportTrustedContext() {
        for (Class<?> requestType : List.of(DynamicFormTemplateCreateReqVO.class,
                DynamicFormTemplatePatchReqVO.class, DynamicFormRevisionPatchReqVO.class,
                DynamicFormInstanceCreateReqVO.class, DynamicFormInstancePatchReqVO.class,
                DynamicFormTemplatePageReqVO.class, DynamicFormInstancePageReqVO.class)) {
            Set<String> fields = allFields(requestType);
            assertTrue(java.util.Collections.disjoint(fields, FORBIDDEN_REQUEST_FIELDS), requestType.getSimpleName());
        }
    }

    @Test
    void templatePatchPreservesExplicitNullAndOmittedFieldsIntoTheCommand() {
        login(9L);
        DynamicFormCommandService commandService = mock(DynamicFormCommandService.class);
        when(commandService.patchTemplate(any())).thenReturn(templateView());
        DynamicFormTemplateController controller = new DynamicFormTemplateController(commandService,
                mock(DynamicFormQueryService.class), new MockEnvironment().withProperty("yudao.tenant.enable", "false"));
        DynamicFormTemplatePatchReqVO request = JsonUtils.parseObject(
                "{\"description\":null}", DynamicFormTemplatePatchReqVO.class);

        controller.patch(11L, 3, request);

        var command = org.mockito.ArgumentCaptor.forClass(DynamicFormCommands.PatchTemplate.class);
        verify(commandService).patchTemplate(command.capture());
        assertFalse(command.getValue().templateName().present());
        assertFalse(command.getValue().categoryCode().present());
        assertTrue(command.getValue().description().present());
        assertNull(command.getValue().description().value());
        assertEquals(0L, command.getValue().actor().tenantId());
        assertEquals(9L, command.getValue().actor().userId());
    }

    private void assertGet(Method method, String path, String permission) {
        assertEquals(List.of(path), List.of(method.getAnnotation(GetMapping.class).value()));
        assertPermission(method, permission);
    }

    private void assertPost(Method method, String path, String permission) {
        assertEquals(List.of(path), List.of(method.getAnnotation(PostMapping.class).value()));
        assertPermission(method, permission);
    }

    private void assertPatch(Method method, String path, String permission) {
        assertEquals(List.of(path), List.of(method.getAnnotation(PatchMapping.class).value()));
        assertPermission(method, permission);
    }

    private void assertPermission(Method method, String permission) {
        assertEquals("@ss.hasPermission('" + permission + "')", method.getAnnotation(PreAuthorize.class).value());
    }

    private void assertHeader(Method method, int parameter, String name) {
        RequestHeader header = method.getParameters()[parameter].getAnnotation(RequestHeader.class);
        assertEquals(name, header.value());
    }

    private Set<String> allFields(Class<?> type) {
        java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Arrays.stream(current.getDeclaredFields()).map(Field::getName).forEach(fields::add);
        }
        return fields;
    }

    private DynamicFormViews.Template templateView() {
        return new DynamicFormViews.Template(11L, "TPL-11", "Example", "GENERAL", null,
                "DISABLED", 4, null, null, null, Set.of("PATCH_TEMPLATE"), null, null);
    }

    private void login(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }
}
