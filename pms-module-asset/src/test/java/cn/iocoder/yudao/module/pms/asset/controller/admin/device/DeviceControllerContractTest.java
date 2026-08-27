package cn.iocoder.yudao.module.pms.asset.controller.admin.device;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceControllerContractTest {

    @Test
    void shouldExposeDevicePageAndDetailEndpoints() throws Exception {
        RequestMapping mapping = DeviceController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/pms/asset/devices"}, mapping.value());
        assertEndpoint("getDevicePage", "/page", "pms:device:query");
        assertEndpoint("getDevice", "/{id}", "pms:device:query");
    }

    @Test
    void shouldExposeAssignmentActionEndpoints() {
        assertActionEndpoint("assignProject", "/{id}/actions/assign-project");
        assertActionEndpoint("assignCustomer", "/{id}/actions/assign-customer");
    }

    @Test
    void shouldKeepAssignmentContextOutOfRequestBodies() throws Exception {
        assertRequestFields("DeviceProjectAssignReqVO", "projectId", "reason");
        assertRequestFields("DeviceCustomerAssignReqVO", "customerId", "relationshipType", "reason");
        assertResponseFields("DeviceAssignmentRespVO", "assignmentVersion", "operationId");
    }

    private static void assertEndpoint(String methodName, String path, String permission) throws Exception {
        Method method = java.util.Arrays.stream(DeviceController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{path}, mapping.value());
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorize);
        assertEquals("@ss.hasPermission('" + permission + "')", authorize.value());
    }

    private static void assertActionEndpoint(String methodName, String path) {
        Method method = java.util.Arrays.stream(DeviceController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{path}, mapping.value());
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorize);
        assertEquals("@ss.hasPermission('pms:device:assign')", authorize.value());
        assertRequiredHeader(method, "Idempotency-Key");
        assertRequiredHeader(method, "If-Match");
    }

    private static void assertRequiredHeader(Method method, String headerName) {
        Parameter parameter = java.util.Arrays.stream(method.getParameters())
                .filter(candidate -> {
                    RequestHeader header = candidate.getAnnotation(RequestHeader.class);
                    return header != null && headerName.equals(header.value());
                })
                .findFirst().orElseThrow();
        RequestHeader header = parameter.getAnnotation(RequestHeader.class);
        assertTrue(header.required());
    }

    private static void assertRequestFields(String simpleName, String... expectedFields) throws Exception {
        Class<?> type = Class.forName(DeviceController.class.getPackageName() + ".vo." + simpleName);
        assertArrayEquals(expectedFields, java.util.Arrays.stream(type.getDeclaredFields())
                .map(java.lang.reflect.Field::getName).toArray(String[]::new));
        assertFalse(java.util.Arrays.stream(type.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .anyMatch(name -> java.util.Set.of("tenantId", "actorId", "requestDigest",
                        "correlationId", "expectedAssignmentVersion").contains(name)));
    }

    private static void assertResponseFields(String simpleName, String... expectedFields) throws Exception {
        Class<?> type = Class.forName(DeviceController.class.getPackageName() + ".vo." + simpleName);
        assertArrayEquals(expectedFields, java.util.Arrays.stream(type.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new));
    }
}
