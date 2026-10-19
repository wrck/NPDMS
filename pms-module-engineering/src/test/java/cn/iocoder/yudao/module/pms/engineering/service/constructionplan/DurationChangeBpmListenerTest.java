package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

class DurationChangeBpmListenerTest {

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void shouldEstablishTrustedTenantZeroWhenSingleTenantIsExplicitlyEnabled() {
        DurationChangeProperties properties = properties();
        DurationChangeBpmResultService service = mock(DurationChangeBpmResultService.class);
        doAnswer(invocation -> {
            assertEquals(0L, TenantContextHolder.getRequiredTenantId());
            return null;
        }).when(service).handle(any());
        DurationChangeBpmListener listener = new DurationChangeBpmListener(properties, service,
                new MockEnvironment().withProperty("yudao.tenant.enable", "false"));

        listener.onApplicationEvent(event());
        verifyNoInteractions(service);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.beforeCommit(false));
        verify(service).handle(any());

        assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void shouldFailClosedWhenMultiTenantContextIsMissing() {
        DurationChangeBpmResultService service = mock(DurationChangeBpmResultService.class);
        DurationChangeBpmListener listener = new DurationChangeBpmListener(properties(), service,
                new MockEnvironment().withProperty("yudao.tenant.enable", "true"));

        assertThrows(ServiceException.class, () -> listener.onApplicationEvent(event()));
        verifyNoInteractions(service);
    }

    @Test
    void shouldIgnoreAnotherProcessDefinition() {
        DurationChangeBpmResultService service = mock(DurationChangeBpmResultService.class);
        DurationChangeBpmListener listener = new DurationChangeBpmListener(properties(), service,
                new MockEnvironment().withProperty("yudao.tenant.enable", "false"));
        BpmProcessInstanceStatusEvent event = event();
        event.setProcessDefinitionKey("another-process");

        listener.onApplicationEvent(event);

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectApprovalOutsideOriginalTransaction() {
        TransactionSynchronizationManager.clear();
        TenantContextHolder.setTenantId(1L);
        DurationChangeBpmResultService service = mock(DurationChangeBpmResultService.class);
        DurationChangeBpmListener listener = new DurationChangeBpmListener(properties(), service,
                new MockEnvironment());

        assertThrows(IllegalStateException.class, () -> listener.onApplicationEvent(event()));
        verifyNoInteractions(service);
    }

    @Test
    void shouldKeepRejectionSynchronousWithOriginalReason() {
        TenantContextHolder.setTenantId(1L);
        DurationChangeBpmResultService service = mock(DurationChangeBpmResultService.class);
        DurationChangeBpmListener listener = new DurationChangeBpmListener(properties(), service,
                new MockEnvironment());
        var rejected = event().setStatus(BpmProcessInstanceStatusEnum.REJECT.getStatus()).setReason("资料不完整");

        listener.onApplicationEvent(rejected);

        verify(service).handle(rejected);
        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
    }

    private DurationChangeProperties properties() {
        DurationChangeProperties properties = new DurationChangeProperties();
        properties.setProcessDefinitionKey("pms-sol-duration-change");
        return properties;
    }

    private BpmProcessInstanceStatusEvent event() {
        return new BpmProcessInstanceStatusEvent(this)
                .setId("P-1")
                .setProcessDefinitionKey("pms-sol-duration-change")
                .setStatus(BpmProcessInstanceStatusEnum.APPROVE.getStatus())
                .setReason("同意");
    }

}
