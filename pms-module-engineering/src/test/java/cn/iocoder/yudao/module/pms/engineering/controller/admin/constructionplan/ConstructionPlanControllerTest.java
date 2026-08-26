package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanApplicationService;
import cn.iocoder.yudao.module.pms.engineering.service.constructionplan.ConstructionPlanQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.CONSTRUCTION_PLAN_PROJECT_FACT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ConstructionPlanControllerTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void singleTenantRequestEstablishesAndClearsTrustedTenantZero() {
        login(9L);
        ConstructionPlanQueryService queryService = mock(ConstructionPlanQueryService.class);
        MockEnvironment environment = new MockEnvironment().withProperty("yudao.tenant.enable", "false");
        ConstructionPlanController controller = new ConstructionPlanController(
                mock(ConstructionPlanApplicationService.class), queryService, environment);

        controller.getByProjectId(100L);

        verify(queryService).getByProjectId(eq(100L),
                eq(new ConstructionPlanQueryService.Actor(0L, 9L)));
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void enabledMultiTenantWithoutContextFailsClosed() {
        login(9L);
        ConstructionPlanQueryService queryService = mock(ConstructionPlanQueryService.class);
        ConstructionPlanController controller = new ConstructionPlanController(
                mock(ConstructionPlanApplicationService.class), queryService,
                new MockEnvironment().withProperty("yudao.tenant.enable", "true"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.getByProjectId(100L));

        assertEquals(CONSTRUCTION_PLAN_PROJECT_FACT_INVALID.getCode(), error.getCode());
        verifyNoInteractions(queryService);
        assertNull(TenantContextHolder.getTenantId());
    }

    private void login(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(userId);
        loginUser.setTenantId(0L);
        SecurityFrameworkUtils.setLoginUser(loginUser, new MockHttpServletRequest());
    }
}
