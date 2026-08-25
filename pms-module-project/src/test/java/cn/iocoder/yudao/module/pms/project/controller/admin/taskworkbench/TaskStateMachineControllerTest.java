package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.service.taskworkbench.TaskStateMachineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TaskStateMachineControllerTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void exposesLockedAdministrationRoutes() throws Exception {
        Method get = TaskStateMachineController.class.getMethod("getPublished");
        Method create = TaskStateMachineController.class.getMethod("createDraft",
                cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo.TaskStateMachineSaveReqVO.class);
        Method publish = TaskStateMachineController.class.getMethod("publish", Long.class, String.class, String.class);

        assertArrayEquals(new String[]{}, get.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{}, create.getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/actions/publish"},
                publish.getAnnotation(PostMapping.class).value());
    }

    @Test
    void explicitSingleTenantModeEstablishesTrustedZeroForCallOnly() {
        TaskStateMachineService service = mock(TaskStateMachineService.class);
        Environment environment = new MockEnvironment().withProperty("yudao.tenant.enable", "false");
        TaskStateMachineController controller = new TaskStateMachineController(service, environment);

        controller.getPublished();

        verify(service).getPublished(eq(0L), eq(null));
        assertNull(TenantContextHolder.getTenantId());
    }
}
