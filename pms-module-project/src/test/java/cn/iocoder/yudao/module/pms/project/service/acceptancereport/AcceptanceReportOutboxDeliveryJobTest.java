package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcceptanceReportOutboxDeliveryJobTest {

    @Mock PlatformOutboxDeliveryApi outboxDeliveryApi;
    @Mock AcceptanceReportSourceProjectionService projectionService;
    @Mock Environment environment;

    @BeforeEach
    void setTenant() {
        TenantContextHolder.setTenantId(7L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void projectionFailureSchedulesRetryWithoutMarkingDelivered() {
        var event = new AcceptanceReportVersionChangedMessage("event-1", 7L, "REVOKED", 100L, 80L,
                "PRELIMINARY", 19L, null, 300L, 2, List.of());
        var message = new PlatformOutboxMessageDTO("event-1", "AcceptanceReportVersionChanged",
                JsonUtils.toJsonString(event), 2, 7L, LocalDateTime.now());
        when(outboxDeliveryApi.claimDue(any())).thenReturn(List.of(message));
        doThrow(new IllegalStateException("projection failed")).when(projectionService).project(any());
        var job = new AcceptanceReportOutboxDeliveryJob(outboxDeliveryApi, projectionService, environment);

        job.execute("");

        verify(outboxDeliveryApi).scheduleRetry(org.mockito.ArgumentMatchers.eq("event-1"),
                org.mockito.ArgumentMatchers.eq(2), any());
        verify(outboxDeliveryApi, never()).markDelivered(any(), org.mockito.ArgumentMatchers.anyInt());
    }
}
