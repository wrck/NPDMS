package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceScopeFactTransactionBoundaryTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void transactionBeginFailureUsesPublicProviderUnavailableBeforeMapperAccess() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any()))
                .thenThrow(new CannotCreateTransactionException("database unavailable"));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DeviceMapper.class, () -> mapper);
            context.registerBean(PlatformTransactionManager.class, () -> transactionManager);
            context.register(TransactionConfig.class, DeviceScopeFactTransactionExecutor.class,
                    DeviceScopeFactApiImpl.class);
            context.refresh();

            DeviceScopeFactApi api = context.getBean(DeviceScopeFactApi.class);
            assertTrue(AopUtils.isAopProxy(context.getBean(DeviceScopeFactTransactionExecutor.class)));
            TenantContextHolder.setTenantId(1L);

            DeviceScopeFactException resolveFailure = assertThrows(DeviceScopeFactException.class,
                    () -> api.resolveBySerials(new DeviceScopeResolveQuery(1L, 10L, List.of("SN-A"))));
            DeviceScopeFactException lockFailure = assertThrows(DeviceScopeFactException.class,
                    () -> api.lockAndRevalidate(revalidation()));

            assertEquals(DeviceScopeFactException.Code.PROVIDER_UNAVAILABLE, resolveFailure.getCode());
            assertEquals(DeviceScopeFactException.Code.PROVIDER_UNAVAILABLE, lockFailure.getCode());
            verify(transactionManager, times(2)).getTransaction(any());
            verifyNoInteractions(mapper);
        }
    }

    private static DeviceScopeRevalidationQuery revalidation() {
        return new DeviceScopeRevalidationQuery(1L, 10L,
                List.of(new DeviceScopeRevalidationQuery.ExpectedDevice(11L, "SN-A", 7L)),
                new DeviceScopeRevalidationQuery.ExpectedScopeWatermark(List.of(
                        new DeviceScopeRevalidationQuery.ExpectedWatermarkEntry(11L, 7L))));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfig {
    }
}
