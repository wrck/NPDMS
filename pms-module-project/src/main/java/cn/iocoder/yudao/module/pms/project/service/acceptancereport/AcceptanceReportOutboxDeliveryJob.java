package cn.iocoder.yudao.module.pms.project.service.acceptancereport;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.service.acceptancereport.event.AcceptanceReportVersionChangedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class AcceptanceReportOutboxDeliveryJob implements JobHandler {

    private static final int BATCH_SIZE = 50;
    private static final long MAX_RETRY_DELAY_MINUTES = 60;
    private final PlatformOutboxDeliveryApi outboxDeliveryApi;
    private final AcceptanceReportSourceProjectionService projectionService;
    private final Environment environment;

    @Override
    @TenantJob
    public String execute(String param) {
        if (TenantContextHolder.getTenantId() != null) {
            return deliverDueEvents();
        }
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            TenantContextHolder.getRequiredTenantId();
        }
        String[] result = new String[1];
        TenantUtils.execute(0L, () -> result[0] = deliverDueEvents());
        return result[0];
    }

    private String deliverDueEvents() {
        LocalDateTime dueAt = LocalDateTime.now();
        List<PlatformOutboxMessageDTO> messages = outboxDeliveryApi.claimDue(new PlatformOutboxClaimQuery(
                dueAt, BATCH_SIZE, Set.of("AcceptanceReportVersionChanged")));
        int delivered = 0;
        int retried = 0;
        for (PlatformOutboxMessageDTO message : messages) {
            try {
                AcceptanceReportVersionChangedMessage event = parse(message);
                projectionService.project(event);
                outboxDeliveryApi.markDelivered(message.eventId(), message.retryCount());
                delivered++;
            } catch (RuntimeException failure) {
                LocalDateTime retryAt = dueAt.plusMinutes(retryDelayMinutes(message.retryCount()));
                outboxDeliveryApi.scheduleRetry(message.eventId(), message.retryCount(), retryAt);
                retried++;
                log.warn("[execute][验收报告事件({})投影失败，计划于({})重试]", message.eventId(), retryAt, failure);
            }
        }
        return String.format("验收报告事件投影成功 %d 条，待重试 %d 条", delivered, retried);
    }

    private AcceptanceReportVersionChangedMessage parse(PlatformOutboxMessageDTO message) {
        if (message == null || !"AcceptanceReportVersionChanged".equals(message.eventType())
                || !Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("invalid acceptance report outbox metadata");
        }
        AcceptanceReportVersionChangedMessage event = JsonUtils.parseObject(
                message.payload(), AcceptanceReportVersionChangedMessage.class);
        if (event == null || !Objects.equals(event.eventId(), message.eventId())
                || !Objects.equals(event.tenantId(), message.tenantId())) {
            throw new IllegalArgumentException("invalid acceptance report outbox payload");
        }
        return event;
    }

    private long retryDelayMinutes(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(1L << exponent, MAX_RETRY_DELAY_MINUTES);
    }
}
