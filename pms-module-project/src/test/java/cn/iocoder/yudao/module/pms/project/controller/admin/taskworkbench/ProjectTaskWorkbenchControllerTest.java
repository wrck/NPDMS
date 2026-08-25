package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectTaskWorkbenchControllerTest {

    @Test
    void shouldExposeLockedReadRoutesWithSharedPermissionTruth() throws Exception {
        RequestMapping root = ProjectTaskWorkbenchController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[]{"/api/v1/pms"}, root.value());
        Map<String, String> routes = Map.of(
                "getWorkspace", "/projects/{id}/workspace",
                "getTasks", "/projects/{id}/tasks",
                "getTask", "/project-tasks/{id}",
                "getWorkbench", "/project-tasks/{id}/workbench");
        for (Map.Entry<String, String> entry : routes.entrySet()) {
            Method method = findMethod(entry.getKey());
            assertArrayEquals(new String[]{entry.getValue()}, method.getAnnotation(GetMapping.class).value());
            assertEquals("@ss.hasPermission('pms:project-task:query')",
                    method.getAnnotation(PreAuthorize.class).value());
        }
    }

    private Method findMethod(String name) {
        return java.util.Arrays.stream(ProjectTaskWorkbenchController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name)).findFirst().orElseThrow();
    }
}
