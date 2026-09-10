package cn.iocoder.yudao.module.pms.project.controller.admin.normalclosure;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.normalclosure.NormalClosureApplicationDO;
import cn.iocoder.yudao.module.pms.project.service.normalclosure.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NormalClosureProcessViewControllerTest {
    @AfterEach void clear() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test void readsApplicationUsingTrustedIdentityAndKeepsQueryPermission() throws Exception {
        TenantContextHolder.setTenantId(1L);
        var user = new LoginUser(); user.setId(9L); user.setTenantId(1L);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
        var query = mock(NormalClosureQueryService.class);
        var controller = new NormalClosureProcessViewController(query, new MockEnvironment());
        var application = new NormalClosureApplicationDO(); application.setId(2099999999999999999L);
        var detail = new NormalClosureViews.ApplicationDetail(10L, application, null, List.of());
        when(query.processViewByApplicationId(eq(application.getId()), any())).thenReturn(detail);

        assertSame(detail, controller.detail(application.getId()).getData());

        var actor = ArgumentCaptor.forClass(NormalClosureAccess.Actor.class);
        verify(query).processViewByApplicationId(eq(application.getId()), actor.capture());
        assertEquals(1L, actor.getValue().tenantId());
        assertEquals(9L, actor.getValue().userId());
        assertNotNull(actor.getValue().correlationId());
        assertEquals("@ss.hasPermission('pms:acc-project-closure:query')",
                NormalClosureProcessViewController.class.getMethod("detail", Long.class)
                        .getAnnotation(PreAuthorize.class).value());
    }
}
