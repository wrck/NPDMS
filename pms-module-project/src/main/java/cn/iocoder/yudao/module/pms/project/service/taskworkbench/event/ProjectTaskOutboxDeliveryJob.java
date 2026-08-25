package cn.iocoder.yudao.module.pms.project.service.taskworkbench.event;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.quartz.core.handler.JobHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.job.TenantJob;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformOutboxDeliveryApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxClaimQuery;
import cn.iocoder.yudao.module.pms.platform.api.outbox.dto.PlatformOutboxMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 领取TaskAssigned Outbox并投递到本地应用事件入口。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProjectTaskOutboxDeliveryJob implements JobHandler {

    static final int BATCH_SIZE = 50;
    static final long MAX_RETRY_DELAY_MINUTES = 60;

    private final PlatformOutboxDeliveryApi outboxDeliveryApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @TenantJob
    public String execute(String param) {
        LocalDateTime dueAt = LocalDateTime.now();
        List<PlatformOutboxMessageDTO> messages = outboxDeliveryApi.claimDue(
                new PlatformOutboxClaimQuery(dueAt, BATCH_SIZE, Set.of("TaskAssigned")));
        int delivered = 0;
        int retried = 0;
        for (PlatformOutboxMessageDTO message : messages) {
            try {
                eventPublisher.publishEvent(toMessage(message));
                outboxDeliveryApi.markDelivered(message.eventId(), message.retryCount());
                delivered++;
            } catch (RuntimeException exception) {
                LocalDateTime nextRetryTime = dueAt.plusMinutes(retryDelayMinutes(message.retryCount()));
                outboxDeliveryApi.scheduleRetry(message.eventId(), message.retryCount(), nextRetryTime);
                retried++;
                log.warn("[execute][任务指派事件({})投递失败，计划于({})重试]",
                        message.eventId(), nextRetryTime, exception);
            }
        }
        return String.format("任务指派事件投递成功 %d 条，待重试 %d 条", delivered, retried);
    }

    private TaskAssignedMessage toMessage(PlatformOutboxMessageDTO message) {
        if (message == null || message.eventId() == null || message.eventId().isBlank()
                || !"TaskAssigned".equals(message.eventType())
                || !Objects.equals(message.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("任务指派事件元数据不完整");
        }
        TaskAssignedMessage.Payload payload = JsonUtils.parseObject(message.payload(),
                TaskAssignedMessage.Payload.class);
        if (payload == null || !Objects.equals(payload.tenantId(), message.tenantId())
                || payload.projectId() == null || payload.projectTaskId() == null
                || payload.assigneeUserId() == null || payload.assignmentId() == null
                || payload.taskVersion() < 1 || payload.assignedBy() == null || payload.occurredAt() == null) {
            throw new IllegalArgumentException("任务指派事件冻结载荷不完整");
        }
        return new TaskAssignedMessage(message.eventId(), payload.tenantId(), payload.projectId(),
                payload.projectTaskId(), payload.assigneeUserId(), payload.assignmentId(), payload.taskVersion(),
                payload.assignedBy(), payload.occurredAt());
    }

    private static long retryDelayMinutes(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(1L << exponent, MAX_RETRY_DELAY_MINUTES);
    }
}
