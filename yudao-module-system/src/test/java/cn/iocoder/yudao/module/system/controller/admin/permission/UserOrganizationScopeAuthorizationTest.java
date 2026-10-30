package cn.iocoder.yudao.module.system.controller.admin.permission;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.system.controller.admin.permission.vo.scope.*;
import cn.iocoder.yudao.module.system.service.permission.UserOrganizationScopeService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserOrganizationScopeAuthorizationTest {
    private AnnotationConfigApplicationContext context;
    private UserOrganizationScopeController controller;
    private UserOrganizationScopeService service;
    private SecurityFrameworkService security;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(ConfigurationForTest.class);
        controller = context.getBean(UserOrganizationScopeController.class);
        service = context.getBean(UserOrganizationScopeService.class);
        security = context.getBean(SecurityFrameworkService.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("scope-operator", null, List.of()));
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); context.close(); }

    @Test
    void noPermissionRejectsEveryEndpointBeforeService() {
        assertThrows(AccessDeniedException.class, () -> controller.getPage(new UserOrganizationScopePageReqVO()));
        assertThrows(AccessDeniedException.class, () -> controller.create(new UserOrganizationScopeSaveReqVO()));
        assertThrows(AccessDeniedException.class, () -> controller.revise(1L, new UserOrganizationScopeSaveReqVO()));
        assertThrows(AccessDeniedException.class, () -> controller.disable(1L, new UserOrganizationScopeDisableReqVO()));
        verifyNoInteractions(service);
    }

    @Test
    void queryPermissionDoesNotGrantMaintenance() {
        when(security.hasAnyPermissions("system:user-scope:query", "system:user-scope:manage")).thenReturn(true);
        var page = new UserOrganizationScopePageReqVO();
        when(service.getPage(page)).thenReturn(PageResult.empty());
        assertEquals(0, controller.getPage(page).getData().getTotal());
        assertThrows(AccessDeniedException.class, () -> controller.create(new UserOrganizationScopeSaveReqVO()));
        assertThrows(AccessDeniedException.class, () -> controller.revise(1L, new UserOrganizationScopeSaveReqVO()));
        assertThrows(AccessDeniedException.class, () -> controller.disable(1L, new UserOrganizationScopeDisableReqVO()));
        verify(service).getPage(page); verifyNoMoreInteractions(service);
    }

    @Test
    void maintenancePermissionAllowsVersionedCommands() {
        when(security.hasPermission("system:user-scope:manage")).thenReturn(true);
        var save = new UserOrganizationScopeSaveReqVO();
        var stop = new UserOrganizationScopeDisableReqVO();
        when(service.create(save)).thenReturn(101L);
        when(service.revise(101L, save)).thenReturn(102L);
        assertEquals(101L, controller.create(save).getData());
        assertEquals(102L, controller.revise(101L, save).getData());
        assertTrue(controller.disable(102L, stop).getData());
        verify(service).disable(102L, stop);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(UserOrganizationScopeController.class)
    static class ConfigurationForTest {
        @Bean UserOrganizationScopeService scopeService() { return mock(UserOrganizationScopeService.class); }
        @Bean(name = "ss") SecurityFrameworkService security() { return mock(SecurityFrameworkService.class); }
    }
}
