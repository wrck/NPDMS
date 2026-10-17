package cn.iocoder.yudao.module.pms.customer.controller.admin.customer;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.customer.controller.admin.customer.vo.CustomerRespVO;
import cn.iocoder.yudao.module.pms.customer.service.query.ProjectCustomerOverviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectCustomerOverviewControllerTest {
    private AnnotationConfigApplicationContext context;
    private ProjectCustomerOverviewController controller;
    private ProjectCustomerOverviewService service;
    private PermissionCheck permissions;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectCustomerOverviewService service() { return mock(ProjectCustomerOverviewService.class); }
        @Bean cn.iocoder.yudao.module.pms.customer.service.customer.CustomerApplicationService commands() { return mock(cn.iocoder.yudao.module.pms.customer.service.customer.CustomerApplicationService.class); }
        @Bean ProjectCustomerOverviewController controller(ProjectCustomerOverviewService service, cn.iocoder.yudao.module.pms.customer.service.customer.CustomerApplicationService commands) {
            return new ProjectCustomerOverviewController(service,commands);
        }
        @Bean(name = "ss") PermissionCheck permissions() { return new PermissionCheck(); }
        @Bean cn.iocoder.yudao.module.pms.customer.service.query.ProjectCustomerLocationService locations() {
            return mock(cn.iocoder.yudao.module.pms.customer.service.query.ProjectCustomerLocationService.class);
        }
        @Bean ProjectCustomerLocationController locationController(cn.iocoder.yudao.module.pms.customer.service.query.ProjectCustomerLocationService locations) {
            return new ProjectCustomerLocationController(locations);
        }
    }

    public static class PermissionCheck {
        private final Set<String> granted = new HashSet<>();
        public boolean hasPermission(String key) { return granted.contains(key); }
    }

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(ProjectCustomerOverviewController.class);
        service = context.getBean(ProjectCustomerOverviewService.class);
        permissions = context.getBean(PermissionCheck.class);
        var user = new LoginUser(); user.setId(31L); user.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        context.close(); SecurityContextHolder.clearContext(); TenantContextHolder.clear();
    }

    @Test
    void eitherFunctionPermissionAloneCannotInvokeProjectCustomerLookup() {
        for (String key : List.of("pms:project:query", "pms:customer:query")) {
            permissions.granted.clear(); permissions.granted.add(key);
            assertThrows(AccessDeniedException.class, () -> controller.get(100L));
        }
        verifyNoInteractions(service);
    }

    @Test
    void locationLookupRequiresBothPermissionsAndPassesOnlyTrustedIdentity() {
        var locations = context.getBean(cn.iocoder.yudao.module.pms.customer.service.query.ProjectCustomerLocationService.class);
        var endpoint = context.getBean(ProjectCustomerLocationController.class);
        for (String key : List.of("pms:project:query", "pms:customer:query")) {
            permissions.granted.clear(); permissions.granted.add(key);
            assertThrows(AccessDeniedException.class, () -> endpoint.get(100L,21L));
        }
        verifyNoInteractions(locations);
        permissions.granted.addAll(Set.of("pms:project:query", "pms:customer:query"));
        endpoint.get(100L,21L);
        verify(locations).get(new cn.iocoder.yudao.module.pms.customer.service.query.ProjectCustomerLocationService.Query(1L,31L,100L,21L));
    }

    @Test
    void bothPermissionsPassOnlyTrustedTenantAndActorToOwnerQuery() {
        permissions.granted.addAll(Set.of("pms:project:query", "pms:customer:query"));
        var query = new ProjectCustomerOverviewService.Query(1L, 31L, 100L);
        var customer = new CustomerRespVO(); customer.setId(200L);
        when(service.get(query)).thenReturn(customer);
        assertSame(customer, controller.get(100L).getData());
        verify(service).get(query);
    }
    @Test
    void projectCustomerWriteRequiresBothPermissionsAndUsesTrustedIdentity() {
        var request = new ProjectCustomerOverviewController.ExtensionRequest("test remark");
        permissions.granted.add("pms:customer:update");
        assertThrows(AccessDeniedException.class,()->controller.update(100L,request,2L,"key"));
        permissions.granted.clear(); permissions.granted.add("pms:project:query");
        assertThrows(AccessDeniedException.class,()->controller.update(100L,request,2L,"key"));
        permissions.granted.add("pms:customer:update");
        controller.update(100L,request,2L,"key");
        verify(context.getBean(cn.iocoder.yudao.module.pms.customer.service.customer.CustomerApplicationService.class)).updateProjectExtension(
                new cn.iocoder.yudao.module.pms.customer.service.customer.command.ProjectCustomerExtensionCommand(1L,100L,"test remark",2L,"key"));
    }
}
