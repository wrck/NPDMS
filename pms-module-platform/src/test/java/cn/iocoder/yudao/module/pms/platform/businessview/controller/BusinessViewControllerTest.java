package cn.iocoder.yudao.module.pms.platform.businessview.controller;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.businessview.*;
import cn.iocoder.yudao.module.pms.platform.controller.admin.businessview.BusinessViewController;
import cn.iocoder.yudao.module.pms.platform.service.businessview.BusinessViewApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** PM-03 / SDS10: exact routes, headers, trusted body and JSON/CommonResult contracts. */
class BusinessViewControllerTest {
    @Test void routesAndPermissionsMatchFormalContract() throws Exception {
        assertEquals("/api/v1/pms/business-views", BusinessViewController.class.getAnnotation(RequestMapping.class).value()[0]);
        Set<String> paths = new TreeSet<>();
        for (Method method : BusinessViewController.class.getDeclaredMethods()) {
            String path = null;
            if (method.isAnnotationPresent(GetMapping.class)) path = "GET " + path(method.getAnnotation(GetMapping.class).value());
            if (method.isAnnotationPresent(PostMapping.class)) path = "POST " + path(method.getAnnotation(PostMapping.class).value());
            if (method.isAnnotationPresent(PutMapping.class)) path = "PUT " + path(method.getAnnotation(PutMapping.class).value());
            if (path != null) { paths.add(path); assertNotNull(method.getAnnotation(PreAuthorize.class)); }
        }
        assertEquals(Set.of("GET ", "GET /components", "GET /{id}", "POST ", "PUT /{id}",
                "POST /{id}/actions/copy", "POST /{id}/actions/validate", "POST /{id}/actions/publish", "POST /{id}/actions/disable"), paths);
        for (String action : List.of("copy", "publish", "disable")) {
            Method method = BusinessViewController.class.getMethod(action, Long.class, Integer.class, String.class);
            assertEquals("If-Match", method.getParameters()[1].getAnnotation(RequestHeader.class).value());
            assertEquals("Idempotency-Key", method.getParameters()[2].getAnnotation(RequestHeader.class).value());
            assertTrue(method.getAnnotation(PreAuthorize.class).value().contains(action.equals("copy") ? ":manage'" : ":" + action + "'"));
        }
        Method validate = BusinessViewController.class.getMethod("validate", Long.class);
        assertTrue(validate.getAnnotation(PreAuthorize.class).value().contains(":manage'"));
        Method update = BusinessViewController.class.getMethod("update", Long.class, Integer.class, String.class, BusinessViewController.SelectionRequest.class);
        assertEquals("If-Match", update.getParameters()[1].getAnnotation(RequestHeader.class).value());
        assertEquals("Idempotency-Key", update.getParameters()[2].getAnnotation(RequestHeader.class).value());
    }
    private String path(String[] value) { return value.length == 0 ? "" : value[0]; }

    @Test void bodyHasExactlyFiveFieldsAndRejectsPrivilegedUnknownJsonFields() {
        assertEquals(Set.of("entityType", "viewKey", "componentKey", "componentVersion", "dynamicFormRevisionId"),
                Arrays.stream(BusinessViewController.SelectionRequest.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
        String json = "{\"entityType\":\"REQUIREMENT_ANALYSIS\",\"viewKey\":\"analysis\",\"componentKey\":\"PROJ_REQUIREMENT_ANALYSIS\",\"componentVersion\":\"1\"}";
        var request = JsonUtils.parseObject(json, BusinessViewController.SelectionRequest.class);
        assertEquals("analysis", request.viewKey());
        for (String forbidden : List.of("tenantId", "actorId", "ownerContext", "viewSource", "contextSchema", "supportedActions", "queryProviderKey")) {
            String injected = json.substring(0, json.length() - 1) + ",\"" + forbidden + "\":\"forged\"}";
            assertThrows(RuntimeException.class, () -> JsonUtils.parseObject(injected, BusinessViewController.SelectionRequest.class), forbidden);
        }
    }

    @Test void controllerReturnsCommonPageAndPassesOnlySelectionAndHeaders() {
        var service = mock(BusinessViewApplicationService.class); var controller = new BusinessViewController(service);
        var page = new BusinessViewController.PageRequest(); page.setPageNo(2); page.setPageSize(10);
        when(service.page(2, 10, null, null)).thenReturn(new PageResult<>(List.of(), 0L));
        assertEquals(0, controller.page(page).getCode()); assertEquals(0L, controller.page(page).getData().getTotal());
        var request = new BusinessViewController.SelectionRequest("REQUIREMENT_ANALYSIS", "analysis", "PROJ_REQUIREMENT_ANALYSIS", "1", null);
        controller.create("key", request); verify(service).create("key", request.selection());
        controller.update(1L, 0, "edit", request); verify(service).update(1L, 0, "edit", request.selection());
        controller.copy(1L, 0, "copy"); verify(service).copy(1L, 0, "copy");
    }

    @Test void responseRoundTripsMetadataAndDoesNotExposeMutableJsonOrObjectActions() {
        var schema = JsonUtils.parseTree("{\"type\":\"object\"}");
        var response = new BusinessViewRevision(1L, "REQUIREMENT_ANALYSIS", "analysis", 1L, "SOL",
                BusinessViewComponentProvider.ViewSource.PAGE, "PROJ_REQUIREMENT_ANALYSIS", "1", null,
                schema, JsonUtils.parseTree("[\"VIEW\"]"), "SOL_QUERY", "SOL_COMMAND", "SOL_PERMISSION", null, null, 0, "DRAFT", Set.of("UPDATE"));
        var json = JsonUtils.toJsonString(response);
        assertEquals(response, JsonUtils.parseObject(json, BusinessViewRevision.class));
        assertFalse(json.contains("tenantId")); assertFalse(json.contains("actorId"));
        ((tools.jackson.databind.node.ObjectNode) schema).put("unsafe", true);
        assertFalse(response.contextSchema().has("unsafe"));
        ((tools.jackson.databind.node.ObjectNode) response.contextSchema()).put("unsafe", true);
        assertFalse(response.contextSchema().has("unsafe"));
    }
}
