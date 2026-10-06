package cn.iocoder.yudao.module.pms.platform.service.delivery;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessAccessGuard;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessEntityAccessPort;
import cn.iocoder.yudao.module.pms.platform.service.file.FileBusinessObjectPolicyRegistry;
import cn.iocoder.yudao.module.pms.platform.support.persistence.BusinessEntityPersistenceRegistry;
import cn.iocoder.yudao.module.pms.platform.support.service.BusinessOperationDispatcher;
import cn.iocoder.yudao.module.pms.project.api.acceptance.ProjectAcceptanceContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The production delivery/file graph must assemble before Owner operation services are used. */
class DeclaredBusinessDeliveryBridgeSpringContextTest {
    @Configuration(proxyBeanMethods = false)
    static class OwnerDependencies {
        @Bean BusinessOperationDispatcher operations(FileBusinessObjectPolicyRegistry filePolicies) {
            // A native operation service uses an entity/form provider backed by these file policies.
            assertNotNull(filePolicies);
            return mock(BusinessOperationDispatcher.class);
        }
        @Bean BusinessEntityAccessPort entities(FileBusinessObjectPolicyRegistry filePolicies) {
            assertNotNull(filePolicies);
            return mock(BusinessEntityAccessPort.class);
        }
    }

    @Test void declaredBridgeDoesNotMakeTheNativeFilePolicyGraphCircular() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(BusinessEntityPersistenceRegistry.class, () -> mock(BusinessEntityPersistenceRegistry.class));
            context.registerBean(BusinessAccessGuard.class, () -> mock(BusinessAccessGuard.class));
            context.registerBean(ProjectScopeApi.class, () -> mock(ProjectScopeApi.class));
            context.registerBean(ProjectAcceptanceContextApi.class, () -> mock(ProjectAcceptanceContextApi.class));
            context.registerBean(DeliveryCatalogService.class, () -> mock(DeliveryCatalogService.class));
            context.register(DeliveryOwnerAccess.class, DeclaredBusinessDeliveryBridge.class,
                    DeliveryFilePolicyProvider.class, FileBusinessObjectPolicyRegistry.class, OwnerDependencies.class);
            assertDoesNotThrow(context::refresh);
            assertNotNull(context.getBean(FileBusinessObjectPolicyRegistry.class));
            assertFalse(context.getBean(DeclaredBusinessDeliveryBridge.class).supports("IT", "missing"));
            var dispatcher = context.getBean(BusinessOperationDispatcher.class);
            assertNotNull(dispatcher);
            verifyNoInteractions(dispatcher, context.getBean(BusinessEntityAccessPort.class));
        }
    }
}
