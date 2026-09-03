package cn.iocoder.yudao.module.pms.project.service.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import cn.iocoder.yudao.module.pms.project.service.satisfaction.event.SatisfactionTaskCreatedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class SatisfactionTaskOutboxDeliveryJob implements JobHandler {
    private static final int BATCH_SIZE = 50;
    private final PlatformOutboxDeliveryApi outboxDeliveryApi;
    private final SatisfactionTaskTodoProjectionService projectionService;
    private final Environment environment;

    @Override
    @TenantJob
    public String execute(String param) {
        if (TenantContextHolder.getTenantId() != null) return deliver();
        if (environment.getProperty("yudao.tenant.enable", Boolean.class, true)) {
            TenantContextHolder.getRequiredTenantId();
        }
        String[] result = new String[1];
        TenantUtils.execute(0L, () -> result[0] = deliver());
        return result[0];
    }

    private String deliver() {
        LocalDateTime now = LocalDateTime.now();
        List<PlatformOutboxMessageDTO> messages = outboxDeliveryApi.claimDue(new PlatformOutboxClaimQuery(
                now, BATCH_SIZE, Set.of("SatisfactionTaskCreated")));
        int delivered = 0;
        int retried = 0;
        for (PlatformOutboxMessageDTO message : messages) {
            try {
                projectionService.project(parse(message));
                outboxDeliveryApi.markDelivered(message.eventId(), message.retryCount());
                delivered++;
            } catch (RuntimeException failure) {
                outboxDeliveryApi.scheduleRetry(message.eventId(), message.retryCount(), now.plusMinutes(1));
                retried++;
                log.warn("[execute][满意度Task事件({})投影失败]", message.eventId(), failure);
            }
        }
        return String.format("满意度Task事件投影成功 %d 条，待重试 %d 条", delivered, retried);
    }

    private SatisfactionTaskCreatedMessage parse(PlatformOutboxMessageDTO message) {
        if (message == null || !"SatisfactionTaskCreated".equals(message.eventType())
                || !Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("invalid satisfaction task outbox metadata");
        }
        SatisfactionTaskCreatedMessage event = JsonUtils.parseObject(message.payload(),
                SatisfactionTaskCreatedMessage.class);
        if (event == null || !Objects.equals(event.eventId(), message.eventId())
                || !Objects.equals(event.tenantId(), message.tenantId())) {
            throw new IllegalArgumentException("invalid satisfaction task outbox payload");
        }
        return event;
    }
}
