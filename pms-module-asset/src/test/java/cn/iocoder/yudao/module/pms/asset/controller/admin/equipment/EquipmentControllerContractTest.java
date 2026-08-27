package cn.iocoder.yudao.module.pms.asset.controller.admin.equipment;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EquipmentControllerContractTest {

    @Test
    void shouldKeepLegacyReadAndWriteEndpoints() {
        RequestMapping mapping = EquipmentController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/pms/equipment"}, mapping.value());
        assertPostEndpoint("createEquipment", "/create", "pms:equipment:create");
        assertPutEndpoint("updateEquipment", "/update", "pms:equipment:update");
        assertDeleteEndpoint("deleteEquipment", "/delete", "pms:equipment:delete");
        assertGetEndpoint("getEquipment", "/get", "pms:equipment:query");
        assertGetEndpoint("getEquipmentPage", "/page", "pms:equipment:query");
        assertPutEndpoint("changeEquipmentStatus", "/status-change", "pms:equipment:status-change");
        assertGetEndpoint("getEquipmentVersionList", "/version/list", "pms:equipment-version:query");
    }

    private static void assertPostEndpoint(String methodName, String path, String permission) {
        Method method = findMethod(methodName);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{path}, mapping.value());
        assertPermission(method, permission);
    }

    private static void assertPutEndpoint(String methodName, String path, String permission) {
        Method method = findMethod(methodName);
        PutMapping mapping = method.getAnnotation(PutMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{path}, mapping.value());
        assertPermission(method, permission);
    }

    private static void assertDeleteEndpoint(String methodName, String path, String permission) {
        Method method = findMethod(methodName);
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{path}, mapping.value());
        assertPermission(method, permission);
    }

    private static void assertGetEndpoint(String methodName, String path, String permission) {
        Method method = findMethod(methodName);
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{path}, mapping.value());
        assertPermission(method, permission);
    }

    private static Method findMethod(String methodName) {
        return java.util.Arrays.stream(EquipmentController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
    }

    private static void assertPermission(Method method, String permission) {
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorize);
        assertEquals("@ss.hasPermission('" + permission + "')", authorize.value());
    }
}
