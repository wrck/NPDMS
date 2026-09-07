package cn.iocoder.yudao.module.pms.project.controller.admin.projects;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.controller.admin.projects.vo.ProjectCreateReqVO;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationApplicationService;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateResult;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.command.ManualProjectCreateCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectMasterControllerTenantContextTest {

    /** PM-01: JSON binding and controller forwarding must preserve optional manual confirmation. */
    @ParameterizedTest
    @NullSource
    @ValueSource(longs = 66L)
    void createPreservesOptionalServiceManagerFromRequest(Long managerId) {
        ProjectManualCreationApplicationService service = mock(ProjectManualCreationApplicationService.class);
        ProjectMasterController controller = new ProjectMasterController();
        ReflectionTestUtils.setField(controller, "projectManualCreationApplicationService", service);
        ReflectionTestUtils.setField(controller, "environment",
                new MockEnvironment().withProperty("yudao.tenant.enable", "true"));
        TenantContextHolder.setTenantId(1L);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(7L);
        loginUser.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
        when(service.create(any(), any())).thenAnswer(invocation -> {
            ManualProjectCreateCommand command = invocation.getArgument(0);
            assertEquals(managerId, command.serviceManagerUserId());
            assertEquals(1L, TenantContextHolder.getTenantId());
            return new ManualProjectCreateResult(100L, "P100", "S0", "ACTIVE", "S0", "UNASSIGNED",
                    1, 910005L, 2, "MANUAL", 1, 1, 0, 0, 0,
                    managerId != null, null, null, null);
        });
        ProjectCreateReqVO request = JsonUtils.parseObject(managerId == null ? "{}"
                : "{\"serviceManagerUserId\":66}", ProjectCreateReqVO.class);

        controller.createProject("manual-manager-create-key", request);

        assertEquals(1L, TenantContextHolder.getTenantId());
    }

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
