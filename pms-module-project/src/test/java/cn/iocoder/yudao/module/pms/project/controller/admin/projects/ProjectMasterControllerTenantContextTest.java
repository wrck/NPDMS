package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectCreateReqVO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectMasterControllerTenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void createInSingleTenantModeEstablishesTrustedZeroForPreparationInitialization() {
        ProjectManualCreationApplicationService service = mock(ProjectManualCreationApplicationService.class);
        ProjectMasterController controller = new ProjectMasterController();
        ReflectionTestUtils.setField(controller, "projectManualCreationApplicationService", service);
        ReflectionTestUtils.setField(controller, "environment",
                new MockEnvironment().withProperty("yudao.tenant.enable", "false"));
        LoginUser loginUser = new LoginUser();
        loginUser.setId(1L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
        when(service.create(any(), any())).thenAnswer(invocation -> {
            ProjectManualCreationApplicationService.Actor actor = invocation.getArgument(1);
            assertEquals(0L, TenantContextHolder.getTenantId());
            assertEquals(0L, actor.tenantId());
            return new ManualProjectCreateResult(100L, "P100", "S0", "ACTIVE", "S0", "UNASSIGNED",
                    1, 910005L, 2, "MANUAL", 1, 1, 0, 0, 0,
                    false, null, null, null);
        });

        controller.createProject("browser-create-key", new ProjectCreateReqVO());

        assertNull(TenantContextHolder.getTenantId());
    }
}
