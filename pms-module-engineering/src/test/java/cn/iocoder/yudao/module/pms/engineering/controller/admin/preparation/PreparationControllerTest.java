package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.service.preparation.PreparationQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.PREPARATION_PROJECT_FACT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PreparationControllerTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void singleTenantCurrentQueryUsesTrustedTenantZeroAndClearsIt() {
        login(9L);
        PreparationQueryService queryService = mock(PreparationQueryService.class);
        PreparationController controller = new PreparationController(queryService,
                new MockEnvironment().withProperty("yudao.tenant.enable", "false"));

        controller.getCurrent(100L, "PRE_02");

        verify(queryService).getCurrent(eq(100L), eq("PRE_02"),
                eq(new PreparationQueryService.Actor(0L, 9L)));
        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void enabledMultiTenantWithoutContextFailsClosed() {
        login(9L);
        PreparationQueryService queryService = mock(PreparationQueryService.class);
        PreparationController controller = new PreparationController(queryService,
                new MockEnvironment().withProperty("yudao.tenant.enable", "true"));

        ServiceException error = assertThrows(ServiceException.class,
                () -> controller.getCurrent(100L, "PRE_02"));

        assertEquals(PREPARATION_PROJECT_FACT_INVALID.getCode(), error.getCode());
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
