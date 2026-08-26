package cn.iocoder.yudao.module.pms.engineering.service.constructionplan;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DurationChangeBpmListenerTest {

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldEstablishTrustedTenantZeroWhenSingleTenantIsExplicitlyEnabled() {
        DurationChangeProperties properties = properties();
        DurationChangeBpmResultService service = mock(DurationChangeBpmResultService.class);
        doAnswer(invocation -> {
            assertEquals(0L, TenantContextHolder.getRequiredTenantId());
            return null;
        }).when(service).handle("P-1", BpmProcessInstanceStatusEnum.APPROVE.getStatus(), "同意");
        DurationChangeBpmListener listener = new DurationChangeBpmListener(properties, service,
                new MockEnvironment().withProperty("yudao.tenant.enable", "false"));

        listener.onApplicationEvent(event());

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
