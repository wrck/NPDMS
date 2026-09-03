package cn.iocoder.yudao.module.pms.asset.controller.admin.producttype;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.service.producttype.AssetProductTypeImportService;
import cn.iocoder.yudao.module.pms.asset.service.producttype.command.ImportAssetProductTypeResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssetProductTypeImportControllerSecurityTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
        TestConfiguration.permissionGranted = false;
    }

    @Test
    void anonymousRequestIsDeniedBeforeServiceInvocation() {
        try (AnnotationConfigApplicationContext context = context()) {
            AssetProductTypeImportController controller = context.getBean(AssetProductTypeImportController.class);

            assertThrows(AccessDeniedException.class,
                    () -> controller.controlledImport("idem-1", request()));

            verify(context.getBean(AssetProductTypeImportService.class), never()).importProductType(any());
        }
    }

    @Test
    void authenticatedUserWithoutDedicatedPermissionIsDeniedBeforeServiceInvocation() {
        authenticate(1L);
        try (AnnotationConfigApplicationContext context = context()) {
            AssetProductTypeImportController controller = context.getBean(AssetProductTypeImportController.class);

            assertThrows(AccessDeniedException.class,
                    () -> controller.controlledImport("idem-1", request()));

            verify(context.getBean(AssetProductTypeImportService.class), never()).importProductType(any());
        }
    }

    @Test
    void authenticatedUserWithDedicatedPermissionInvokesControllerService() {
        authenticate(1L);
        TestConfiguration.permissionGranted = true;
        try (AnnotationConfigApplicationContext context = context()) {
            AssetProductTypeImportService service = context.getBean(AssetProductTypeImportService.class);
            when(service.importProductType(any())).thenReturn(
                    new ImportAssetProductTypeResult(11L, 12L, "TYPE-A", false));

            var result = context.getBean(AssetProductTypeImportController.class)
                    .controlledImport("idem-1", request());

            assertEquals(11L, result.getData().productTypeId());
            verify(service).importProductType(any());
        }
    }

    private AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(TestConfiguration.class);
    }

    private void authenticate(Long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(9L);
        user.setTenantId(tenantId);
        SecurityFrameworkUtils.setLoginUser(user, new MockHttpServletRequest());
        TenantContextHolder.setTenantId(tenantId);
    }

    private cn.iocoder.yudao.module.pms.asset.controller.admin.producttype.vo.ImportAssetProductTypeReqVO request() {
        var request = new cn.iocoder.yudao.module.pms.asset.controller.admin.producttype.vo.ImportAssetProductTypeReqVO();
        request.setOperationId("op-1");
        request.setProductTypeCode("TYPE-A");
        request.setDisplayName("类型A");
        request.setEnabled(true);
        request.setSourceSystem("CRM");
        request.setSourceKey("source-1");
        request.setSourceVersion("v1");
        request.setSourceUpdatedAt(java.time.LocalDateTime.of(2026, 8, 31, 10, 0));
        request.setPayloadHash("a".repeat(64));
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfiguration {
        private static volatile boolean permissionGranted;

        @Bean("ss")
        SecurityFrameworkService securityFrameworkService() {
            return (SecurityFrameworkService) Proxy.newProxyInstance(
                    SecurityFrameworkService.class.getClassLoader(),
                    new Class<?>[]{SecurityFrameworkService.class},
                    (proxy, method, args) -> method.getReturnType() == boolean.class && permissionGranted);
        }

        @Bean
        AssetProductTypeImportService importService() {
            return mock(AssetProductTypeImportService.class);
        }

        @Bean
        AssetProductTypeImportController controller(AssetProductTypeImportService importService) {
            return new AssetProductTypeImportController(importService);
        }
    }
}
