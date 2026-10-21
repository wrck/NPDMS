package cn.iocoder.yudao.module.pms.acceptance.service.satisfaction;

import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.satisfaction.SatisfactionTemplateController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SatisfactionTemplateSecurityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Config {
        @Bean SecurityFrameworkService ss() { return mock(SecurityFrameworkService.class); }
        @Bean SatisfactionTemplateManagementService service() { return mock(SatisfactionTemplateManagementService.class); }
        @Bean SatisfactionTemplateController controller(SatisfactionTemplateManagementService service) {
            return new SatisfactionTemplateController(service, new MockEnvironment());
        }
    }

    @Test void businessManagementPermissionCannotConfigureTemplatesWithoutAdministratorRole() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            TenantContextHolder.setTenantId(1L);
            var ss = context.getBean(SecurityFrameworkService.class);
            var service = context.getBean(SatisfactionTemplateManagementService.class);
            var controller = context.getBean(SatisfactionTemplateController.class);
            when(ss.hasPermission("pms:acceptance:satisfaction:manage")).thenReturn(true);
            assertThrows(AccessDeniedException.class, () -> controller.create(new SatisfactionTemplateController.TemplateCreateReqVO()));
            assertThrows(AccessDeniedException.class, () -> controller.createRevision(1L, new SatisfactionTemplateController.RevisionCreateReqVO()));
            assertThrows(AccessDeniedException.class, () -> controller.publish(1L, 2L, "publish", new SatisfactionTemplateController.PublishReqVO()));
            verifyNoInteractions(service);
            when(ss.hasRole("super_admin")).thenReturn(true);
            assertEquals(0, controller.create(new SatisfactionTemplateController.TemplateCreateReqVO()).getCode());
            verify(service).create(eq(1L), isNull(), any());
            when(ss.hasPermission("pms:acceptance:satisfaction:manage")).thenReturn(false);
            assertThrows(AccessDeniedException.class, () -> controller.create(new SatisfactionTemplateController.TemplateCreateReqVO()));
        } finally { TenantContextHolder.clear(); }
    }
}
