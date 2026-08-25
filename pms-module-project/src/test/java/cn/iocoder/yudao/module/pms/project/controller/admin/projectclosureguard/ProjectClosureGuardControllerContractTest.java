package cn.iocoder.yudao.module.pms.project.controller.admin.projectclosureguard;

import cn.iocoder.yudao.module.pms.project.controller.admin.projectclosure.ProjectClosureController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectClosureGuardControllerContractTest {

    @Test
    void closureGuardEndpointMatchesFormalContract() throws NoSuchMethodException {
        RequestMapping root = ProjectClosureGuardController.class.getAnnotation(RequestMapping.class);
        assertEquals("/pms/closure-gates", root.value()[0]);
        Method method = ProjectClosureGuardController.class.getDeclaredMethod("evaluate", Long.class, long.class);
        assertEquals("/{projectId}", method.getAnnotation(GetMapping.class).value()[0]);
        PreAuthorize permission = method.getAnnotation(PreAuthorize.class);
        assertNotNull(permission);
        assertEquals("@ss.hasPermission('pms:project:query')", permission.value());
    }

    @Test
    void closureSubmitRequiresPinnedTreeVersion() {
        Method submit = Arrays.stream(ProjectClosureController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("submit"))
                .findFirst().orElseThrow();

        assertTrue(Arrays.stream(submit.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .anyMatch(header -> header != null && "If-Match".equals(header.value()) && header.required()));
    }
}
